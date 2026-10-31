package br.com.lucena.erp.portal;

import br.com.lucena.erp.academic.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Cálculo do desempenho de alunos em uma disciplina da turma.
 * <ul>
 *   <li>Média = Σ(nota/nota máxima × 10 × peso) / Σ pesos das avaliações regulares (não recuperação).
 *       Durante o período considera apenas avaliações já lançadas; com o período encerrado, nota ausente vale 0.</li>
 *   <li>Recuperação: a nota final é a maior entre a média e a nota de recuperação (normalizada para 0–10).</li>
 *   <li>Frequência = (aulas − faltas) / aulas × 100.</li>
 *   <li>Situação final (período encerrado): reprovado por frequência tem precedência; depois reprovado por nota;
 *       caso contrário aprovado. Mínimos vêm das configurações da faculdade.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class GradeCalculator {
    private static final BigDecimal TEN = BigDecimal.TEN;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final AssessmentRepository assessments;
    private final AttendanceSessionRepository sessions;
    private final JdbcTemplate jdbc;

    public enum Situation { EM_CURSO, APROVADO, REPROVADO_NOTA, REPROVADO_FREQUENCIA }

    public record Result(UUID studentId, BigDecimal average, BigDecimal recovery, BigDecimal finalGrade, int totalLessons,
                         int absences, BigDecimal attendancePct, Situation situation, boolean riskGrade, boolean riskAttendance) {
    }

    public record Rules(BigDecimal minGrade, BigDecimal minAttendancePct) {
    }

    public Rules rules(UUID facultyId) {
        return jdbc.query("select academic_min_grade, academic_min_attendance_pct from faculty_settings where faculty_id = ?",
                rs -> rs.next() ? new Rules(rs.getBigDecimal(1), rs.getBigDecimal(2)) : new Rules(new BigDecimal("6.00"), new BigDecimal("75.00")),
                facultyId);
    }

    /** Resultados para os alunos informados em uma disciplina da turma. */
    public Map<UUID, Result> compute(UUID facultyId, UUID classSubjectId, Collection<UUID> studentIds, boolean termClosed) {
        Rules rules = rules(facultyId);
        List<Assessment> list = assessments.findByClassSubjectIdOrderByDueDateAscCreatedAtAsc(classSubjectId);
        Map<UUID, Map<UUID, BigDecimal>> scores = new HashMap<>(); // aluno -> avaliação -> nota
        jdbc.query("""
                select g.student_id, g.assessment_id, g.score from grades g join assessments a on a.id = g.assessment_id
                where a.class_subject_id = ?""", rs -> {
            scores.computeIfAbsent(rs.getObject(1, UUID.class), k -> new HashMap<>()).put(rs.getObject(2, UUID.class), rs.getBigDecimal(3));
        }, classSubjectId);
        int totalLessons = sessions.findByClassSubjectIdOrderBySessionDateDesc(classSubjectId).stream().mapToInt(AttendanceSession::getLessons).sum();
        Map<UUID, Integer> absences = new HashMap<>();
        jdbc.query("""
                select a.student_id, sum(a.absences) from attendance a join attendance_sessions s on s.id = a.session_id
                where s.class_subject_id = ? group by a.student_id""",
                rs -> { absences.put(rs.getObject(1, UUID.class), rs.getInt(2)); }, classSubjectId);

        Map<UUID, Result> out = new LinkedHashMap<>();
        for (UUID sid : studentIds) {
            Map<UUID, BigDecimal> mine = scores.getOrDefault(sid, Map.of());
            BigDecimal weighted = BigDecimal.ZERO, weights = BigDecimal.ZERO, recovery = null;
            for (Assessment a : list) {
                BigDecimal score = mine.get(a.getId());
                if (a.getKind() == Assessment.Kind.RECUPERACAO) {
                    if (score != null) {
                        BigDecimal n = normalize(score, a.getMaxScore());
                        recovery = recovery == null ? n : recovery.max(n);
                    }
                    continue;
                }
                if (score == null && !termClosed) continue;
                weighted = weighted.add(normalize(score == null ? BigDecimal.ZERO : score, a.getMaxScore()).multiply(a.getWeight()));
                weights = weights.add(a.getWeight());
            }
            BigDecimal average = weights.signum() == 0 ? null : weighted.divide(weights, 2, RoundingMode.HALF_UP);
            BigDecimal finalGrade = average == null ? recovery : (recovery == null ? average : average.max(recovery));
            int abs = absences.getOrDefault(sid, 0);
            BigDecimal pct = totalLessons == 0 ? HUNDRED.setScale(2)
                    : BigDecimal.valueOf(totalLessons - abs).multiply(HUNDRED).divide(BigDecimal.valueOf(totalLessons), 2, RoundingMode.HALF_UP);
            boolean riskGrade = finalGrade != null && finalGrade.compareTo(rules.minGrade()) < 0;
            boolean riskAttendance = pct.compareTo(rules.minAttendancePct()) < 0;
            Situation situation = Situation.EM_CURSO;
            if (termClosed) {
                if (riskAttendance) situation = Situation.REPROVADO_FREQUENCIA;
                else if (finalGrade == null || riskGrade) situation = Situation.REPROVADO_NOTA;
                else situation = Situation.APROVADO;
            }
            out.put(sid, new Result(sid, average, recovery, finalGrade, totalLessons, abs, pct, situation, riskGrade, riskAttendance));
        }
        return out;
    }

    static BigDecimal normalize(BigDecimal score, BigDecimal max) {
        return score.multiply(TEN).divide(max, 4, RoundingMode.HALF_UP);
    }
}
