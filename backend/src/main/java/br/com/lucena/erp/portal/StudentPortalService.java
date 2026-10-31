package br.com.lucena.erp.portal;

import br.com.lucena.erp.academic.Assessment;
import br.com.lucena.erp.common.ApiException;
import br.com.lucena.erp.portal.PortalDtos.*;
import br.com.lucena.erp.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

/** Portal do aluno: somente dados do próprio aluno (vínculo students.user_id). */
@Service
@RequiredArgsConstructor
public class StudentPortalService {
    private final GradeCalculator calculator;
    private final JdbcTemplate jdbc;

    record Me(UUID id, UUID facultyId, String registration, String name, String status) {
    }

    record Membership(UUID classId, String classCode, int semester, UUID courseId, String courseName, String termCode, String termStatus) {
    }

    @Transactional(readOnly = true)
    public StudentOverview overview(AuthPrincipal p) {
        Me me = me(p);
        GradeCalculator.Rules rules = calculator.rules(me.facultyId());
        Membership m = memberships(me).stream().findFirst().orElse(null);
        if (m == null) {
            return new StudentOverview(me.id(), me.registration(), me.name(), me.status(), null, null, null, null, null,
                    rules.minGrade(), rules.minAttendancePct(), List.of(), List.of());
        }
        List<SubjectSummary> subjects = subjects(me, m);
        List<ScheduleItem> schedule = jdbc.query("""
                select cs.id, s.name, t.full_name, cs.weekday, cs.start_time, cs.end_time, c.room
                from class_subjects cs join subjects s on s.id = cs.subject_id join classes c on c.id = cs.class_id
                left join teachers t on t.id = cs.teacher_id where cs.class_id = ? and cs.weekday is not null
                order by cs.weekday, cs.start_time""",
                (rs, i) -> new ScheduleItem(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getShort(4),
                        rs.getObject(5, LocalTime.class), rs.getObject(6, LocalTime.class), rs.getString(7)), m.classId());
        return new StudentOverview(me.id(), me.registration(), me.name(), me.status(), m.courseName(), m.classCode(), m.termCode(),
                m.termStatus(), m.semester(), rules.minGrade(), rules.minAttendancePct(), schedule, subjects);
    }

    @Transactional(readOnly = true)
    public MySubjectDetail subject(AuthPrincipal p, UUID csId) {
        Me me = me(p);
        // a disciplina precisa ser de uma turma da qual o aluno participa
        Membership m = memberships(me).stream().filter(x -> Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from class_subjects where id = ? and class_id = ?)", Boolean.class, csId, x.classId())))
                .findFirst().orElseThrow(() -> ApiException.notFound("Disciplina"));
        SubjectSummary summary = subjects(me, m).stream().filter(s -> s.classSubjectId().equals(csId)).findFirst().orElseThrow();
        Map<String, String> texts = jdbc.queryForObject("select cs.teaching_plan, s.syllabus from class_subjects cs join subjects s on s.id = cs.subject_id where cs.id = ?",
                (rs, i) -> { Map<String, String> t = new HashMap<>(); t.put("plan", rs.getString(1)); t.put("syllabus", rs.getString(2)); return t; }, csId);
        List<MyAssessment> assessments = jdbc.query("""
                select a.title, a.kind, a.weight, a.max_score, a.due_date, g.score from assessments a
                left join grades g on g.assessment_id = a.id and g.student_id = ?
                where a.class_subject_id = ? order by a.due_date nulls last, a.created_at""",
                (rs, i) -> new MyAssessment(rs.getString(1), Assessment.Kind.valueOf(rs.getString(2)), rs.getBigDecimal(3), rs.getBigDecimal(4),
                        rs.getObject(5, LocalDate.class), rs.getBigDecimal(6)), me.id(), csId);
        List<MyAttendance> attendance = jdbc.query("""
                select s.session_date, s.lessons, s.content, coalesce(a.absences, 0), coalesce(a.justified, false) from attendance_sessions s
                left join attendance a on a.session_id = s.id and a.student_id = ?
                where s.class_subject_id = ? order by s.session_date desc""",
                (rs, i) -> new MyAttendance(rs.getObject(1, LocalDate.class), rs.getShort(2), rs.getString(3), rs.getInt(4), rs.getBoolean(5)),
                me.id(), csId);
        return new MySubjectDetail(summary, texts.get("plan"), texts.get("syllabus"), assessments, attendance);
    }

    @Transactional(readOnly = true)
    public List<HistoryTerm> history(AuthPrincipal p) {
        Me me = me(p);
        return memberships(me).stream().map(m -> new HistoryTerm(m.termCode(), m.termStatus(), m.classCode(), m.courseName(), subjects(me, m))).toList();
    }

    /** Situação de cada disciplina da matriz do curso atual (aprovada, cursando, reprovada ou a cursar). */
    @Transactional(readOnly = true)
    public CurriculumProgress curriculum(AuthPrincipal p) {
        Me me = me(p);
        List<Membership> ms = memberships(me);
        if (ms.isEmpty()) return new CurriculumProgress(null, 0, 0, 0, 0, BigDecimal.ZERO, List.of());
        UUID courseId = ms.get(0).courseId();
        Map<UUID, String> statusBySubject = new HashMap<>();
        // percorre do mais antigo ao mais recente: o resultado mais recente prevalece, mas aprovação nunca é perdida
        List<Membership> ordered = new ArrayList<>(ms);
        Collections.reverse(ordered);
        for (Membership m : ordered) {
            if (!m.courseId().equals(courseId)) continue;
            Map<UUID, UUID> subjectByCs = new HashMap<>();
            jdbc.query("select id, subject_id from class_subjects where class_id = ?", rs -> { subjectByCs.put(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)); }, m.classId());
            for (SubjectSummary s : subjects(me, m)) {
                UUID subjectId = subjectByCs.get(s.classSubjectId());
                String st = switch (s.result().situation()) {
                    case APROVADO -> "APROVADA";
                    case EM_CURSO -> "CURSANDO";
                    default -> "REPROVADA";
                };
                if (!"APROVADA".equals(statusBySubject.get(subjectId))) statusBySubject.put(subjectId, st);
            }
        }
        String courseName = ms.get(0).courseName();
        List<CurriculumProgressItem> items = jdbc.query("""
                select s.id, s.code, s.name, cu.semester, cu.mandatory, s.workload_hours, s.credits from curriculum cu
                join subjects s on s.id = cu.subject_id where cu.course_id = ? order by cu.semester, s.name""",
                (rs, i) -> new CurriculumProgressItem(rs.getString(2), rs.getString(3), rs.getInt(4), rs.getBoolean(5), rs.getInt(6), rs.getInt(7),
                        statusBySubject.getOrDefault(rs.getObject(1, UUID.class), "A_CURSAR")), courseId);
        int totalCredits = items.stream().mapToInt(CurriculumProgressItem::credits).sum();
        int totalHours = items.stream().mapToInt(CurriculumProgressItem::workloadHours).sum();
        int doneCredits = items.stream().filter(i -> "APROVADA".equals(i.status())).mapToInt(CurriculumProgressItem::credits).sum();
        int doneHours = items.stream().filter(i -> "APROVADA".equals(i.status())).mapToInt(CurriculumProgressItem::workloadHours).sum();
        BigDecimal pct = totalHours == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(doneHours * 100L).divide(BigDecimal.valueOf(totalHours), 2, RoundingMode.HALF_UP);
        return new CurriculumProgress(courseName, totalCredits, doneCredits, totalHours, doneHours, pct, items);
    }

    private List<SubjectSummary> subjects(Me me, Membership m) {
        List<SubjectSummary> base = jdbc.query("""
                select cs.id, s.code, s.name, t.full_name, s.workload_hours from class_subjects cs join subjects s on s.id = cs.subject_id
                left join teachers t on t.id = cs.teacher_id where cs.class_id = ? order by s.name""",
                (rs, i) -> new SubjectSummary(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5), null),
                m.classId());
        boolean closed = "CLOSED".equals(m.termStatus());
        return base.stream().map(s -> new SubjectSummary(s.classSubjectId(), s.subjectCode(), s.subjectName(), s.teacherName(), s.workloadHours(),
                calculator.compute(me.facultyId(), s.classSubjectId(), List.of(me.id()), closed).get(me.id()))).toList();
    }

    /** Turmas do aluno, da mais recente para a mais antiga. */
    private List<Membership> memberships(Me me) {
        return jdbc.query("""
                select c.id, c.code, c.semester, co.id, co.name, t.code, t.status from class_students x join classes c on c.id = x.class_id
                join courses co on co.id = c.course_id join academic_terms t on t.id = c.term_id
                where x.student_id = ? order by t.start_date desc""",
                (rs, i) -> new Membership(rs.getObject(1, UUID.class), rs.getString(2), rs.getInt(3), rs.getObject(4, UUID.class), rs.getString(5),
                        rs.getString(6), rs.getString(7)), me.id());
    }

    private Me me(AuthPrincipal p) {
        Me me = jdbc.query("""
                select id, faculty_id, registration, coalesce(social_name, full_name), status from students where user_id = ? and faculty_id = ?""",
                rs -> rs.next() ? new Me(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4), rs.getString(5)) : null,
                p.userId(), p.facultyId());
        if (me == null) throw ApiException.notFound("Cadastro de aluno vinculado a este usuário");
        return me;
    }
}
