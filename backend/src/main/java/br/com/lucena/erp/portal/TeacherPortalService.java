package br.com.lucena.erp.portal;

import br.com.lucena.erp.academic.*;
import br.com.lucena.erp.audit.AuditService;
import br.com.lucena.erp.common.ApiException;
import br.com.lucena.erp.common.ClientInfo;
import br.com.lucena.erp.portal.PortalDtos.*;
import br.com.lucena.erp.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * Portal do professor. Toda operação verifica que a disciplina da turma pertence ao professor autenticado
 * (vínculo teachers.user_id → class_subjects.teacher_id); caso contrário, responde 404.
 */
@Service
@RequiredArgsConstructor
public class TeacherPortalService {
    static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private final ClassSubjectRepository classSubjects;
    private final AssessmentRepository assessments;
    private final AttendanceSessionRepository sessions;
    private final GradeCalculator calculator;
    private final AuditService audit;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    /** Contexto validado de uma disciplina da turma do professor. */
    record Ctx(UUID facultyId, UUID teacherId, ClassSubject cs, UUID termId, String termStatus, LocalDate termStart, LocalDate termEnd) {
        boolean closed() {
            return "CLOSED".equals(termStatus);
        }
    }

    // ======================= Consultas =======================

    @Transactional(readOnly = true)
    public List<TeachingView> myTeaching(AuthPrincipal p) {
        UUID teacherId = teacherId(p);
        return jdbc.query(TEACHING_SQL + " where cs.teacher_id = ? order by t.code desc, c.code, s.name", (rs, i) -> teaching(rs), teacherId);
    }

    @Transactional(readOnly = true)
    public DiaryView diary(AuthPrincipal p, UUID csId) {
        Ctx ctx = ctx(p, csId);
        TeachingView tv = jdbc.queryForObject(TEACHING_SQL + " where cs.id = ?", (rs, i) -> teaching(rs), csId);
        String syllabus = jdbc.queryForObject("select syllabus from subjects where id = ?", String.class, ctx.cs().getSubjectId());
        List<Assessment> list = assessments.findByClassSubjectIdOrderByDueDateAscCreatedAtAsc(csId);
        Map<UUID, Long> graded = new HashMap<>();
        jdbc.query("select assessment_id, count(*) from grades g join assessments a on a.id = g.assessment_id where a.class_subject_id = ? group by assessment_id",
                rs -> { graded.put(rs.getObject(1, UUID.class), rs.getLong(2)); }, csId);
        List<AssessmentView> av = list.stream().map(a -> new AssessmentView(a.getId(), a.getTitle(), a.getKind(), a.getWeight(), a.getMaxScore(),
                a.getDueDate(), graded.getOrDefault(a.getId(), 0L))).toList();

        List<Object[]> roster = roster(ctx);
        List<UUID> ids = roster.stream().map(r -> (UUID) r[0]).toList();
        Map<UUID, Map<UUID, BigDecimal>> scores = new HashMap<>();
        jdbc.query("select g.student_id, g.assessment_id, g.score from grades g join assessments a on a.id = g.assessment_id where a.class_subject_id = ?",
                rs -> { scores.computeIfAbsent(rs.getObject(1, UUID.class), k -> new HashMap<>()).put(rs.getObject(2, UUID.class), rs.getBigDecimal(3)); }, csId);
        Map<UUID, GradeCalculator.Result> results = calculator.compute(ctx.facultyId(), csId, ids, ctx.closed());
        List<StudentRow> rows = roster.stream().map(r -> new StudentRow((UUID) r[0], (String) r[1], (String) r[2], (String) r[3],
                scores.getOrDefault((UUID) r[0], Map.of()), results.get((UUID) r[0]))).toList();
        GradeCalculator.Rules rules = calculator.rules(ctx.facultyId());
        return new DiaryView(tv, ctx.cs().getTeachingPlan(), syllabus, rules.minGrade(), rules.minAttendancePct(), av, rows,
                "OPEN".equals(ctx.termStatus()));
    }

    // ======================= Plano de ensino =======================

    @Transactional
    public DiaryView updatePlan(AuthPrincipal p, UUID csId, PlanRequest req, ClientInfo client) {
        Ctx ctx = ctx(p, csId);
        ensureNotClosed(ctx);
        String plan = req.teachingPlan() == null || req.teachingPlan().isBlank() ? null : req.teachingPlan().trim();
        ctx.cs().setTeachingPlan(plan);
        classSubjects.flush();
        audit.record(p, ctx.facultyId(), "TEACHING_PLAN_UPDATED", "CLASS_SUBJECT", csId, Map.of("length", plan == null ? 0 : plan.length()), client);
        return diary(p, csId);
    }

    // ======================= Avaliações =======================

    @Transactional
    public DiaryView createAssessment(AuthPrincipal p, UUID csId, AssessmentRequest req, ClientInfo client) {
        Ctx ctx = ctx(p, csId);
        ensureEditable(ctx);
        validateDue(ctx, req.dueDate());
        Assessment a = new Assessment();
        a.setFacultyId(ctx.facultyId());
        a.setClassSubjectId(csId);
        apply(a, req);
        assessments.save(a);
        audit.record(p, ctx.facultyId(), "ASSESSMENT_CREATED", "ASSESSMENT", a.getId(),
                Map.of("title", a.getTitle(), "kind", a.getKind().name(), "weight", a.getWeight().toPlainString(), "maxScore", a.getMaxScore().toPlainString()), client);
        return diary(p, csId);
    }

    @Transactional
    public DiaryView updateAssessment(AuthPrincipal p, UUID csId, UUID aid, AssessmentRequest req, ClientInfo client) {
        Ctx ctx = ctx(p, csId);
        ensureEditable(ctx);
        validateDue(ctx, req.dueDate());
        Assessment a = assessments.findByIdAndClassSubjectId(aid, csId).orElseThrow(() -> ApiException.notFound("Avaliação"));
        BigDecimal highest = jdbc.queryForObject("select max(score) from grades where assessment_id = ?", BigDecimal.class, aid);
        if (highest != null && req.maxScore().compareTo(highest) < 0) {
            throw ApiException.badRequest("MAX_SCORE_BELOW_GRADES", "Já existem notas maiores que " + req.maxScore().toPlainString() + " nesta avaliação");
        }
        Map<String, Object> before = Map.of("title", a.getTitle(), "kind", a.getKind().name(), "weight", a.getWeight().toPlainString(),
                "maxScore", a.getMaxScore().toPlainString());
        apply(a, req);
        audit.record(p, ctx.facultyId(), "ASSESSMENT_UPDATED", "ASSESSMENT", aid, Map.of("before", before), client);
        return diary(p, csId);
    }

    @Transactional
    public DiaryView deleteAssessment(AuthPrincipal p, UUID csId, UUID aid, ClientInfo client) {
        Ctx ctx = ctx(p, csId);
        ensureEditable(ctx);
        Assessment a = assessments.findByIdAndClassSubjectId(aid, csId).orElseThrow(() -> ApiException.notFound("Avaliação"));
        Long graded = jdbc.queryForObject("select count(*) from grades where assessment_id = ?", Long.class, aid);
        if (graded != null && graded > 0) {
            throw ApiException.conflict("ASSESSMENT_HAS_GRADES", "A avaliação já tem " + graded + " nota(s) lançada(s). Remova as notas antes de excluir");
        }
        assessments.delete(a);
        audit.record(p, ctx.facultyId(), "ASSESSMENT_DELETED", "ASSESSMENT", aid, Map.of("title", a.getTitle()), client);
        return diary(p, csId);
    }

    private void apply(Assessment a, AssessmentRequest req) {
        a.setTitle(req.title().trim());
        a.setKind(req.kind());
        a.setWeight(req.weight());
        a.setMaxScore(req.maxScore());
        a.setDueDate(req.dueDate());
    }

    private void validateDue(Ctx ctx, LocalDate due) {
        if (due != null && (due.isBefore(ctx.termStart()) || due.isAfter(ctx.termEnd()))) {
            throw ApiException.badRequest("DATE_OUTSIDE_TERM", "A data deve estar dentro do período letivo");
        }
    }

    // ======================= Notas =======================

    /** Lança/altera/remove (score null) notas de uma avaliação; cada alteração é auditada com valor anterior e novo. */
    @Transactional
    public DiaryView saveGrades(AuthPrincipal p, UUID csId, UUID aid, GradesRequest req, ClientInfo client) {
        Ctx ctx = ctx(p, csId);
        ensureEditable(ctx);
        Assessment a = assessments.findByIdAndClassSubjectId(aid, csId).orElseThrow(() -> ApiException.notFound("Avaliação"));
        Set<UUID> roster = new HashSet<>(roster(ctx).stream().map(r -> (UUID) r[0]).toList());
        Set<UUID> seen = new HashSet<>();
        for (GradeEntry g : req.grades()) {
            if (!roster.contains(g.studentId())) throw ApiException.badRequest("STUDENT_NOT_IN_CLASS", "Aluno não pertence a esta turma");
            if (!seen.add(g.studentId())) throw ApiException.badRequest("DUPLICATE_STUDENT", "Aluno repetido na lista de notas");
            if (g.score() != null && g.score().compareTo(a.getMaxScore()) > 0) {
                throw ApiException.badRequest("SCORE_ABOVE_MAX", "Nota acima do máximo da avaliação (" + a.getMaxScore().toPlainString() + ")");
            }
        }
        for (GradeEntry g : req.grades()) {
            BigDecimal before = jdbc.query("select score from grades where assessment_id = ? and student_id = ?",
                    rs -> rs.next() ? rs.getBigDecimal(1) : null, aid, g.studentId());
            BigDecimal after = g.score() == null ? null : g.score().setScale(2, java.math.RoundingMode.HALF_UP);
            if (Objects.equals(before == null ? null : before.stripTrailingZeros(), after == null ? null : after.stripTrailingZeros())) continue;
            if (after == null) {
                jdbc.update("delete from grades where assessment_id = ? and student_id = ?", aid, g.studentId());
            } else {
                jdbc.update("""
                        insert into grades (faculty_id, assessment_id, student_id, score, updated_by, updated_at) values (?, ?, ?, ?, ?, now())
                        on conflict (assessment_id, student_id) do update set score = excluded.score, updated_by = excluded.updated_by, updated_at = now()""",
                        ctx.facultyId(), aid, g.studentId(), after, p.userId());
            }
            Map<String, Object> d = new HashMap<>();
            d.put("assessment", a.getTitle());
            d.put("studentId", g.studentId().toString());
            d.put("before", before == null ? "" : before.toPlainString());
            d.put("after", after == null ? "" : after.toPlainString());
            audit.record(p, ctx.facultyId(), "GRADE_CHANGED", "CLASS_SUBJECT", csId, d, client);
        }
        return diary(p, csId);
    }

    // ======================= Frequência (diário de classe) =======================

    @Transactional(readOnly = true)
    public List<SessionView> sessions(AuthPrincipal p, UUID csId) {
        ctx(p, csId);
        Map<UUID, List<AbsenceEntry>> abs = new HashMap<>();
        jdbc.query("select a.session_id, a.student_id, a.absences, a.justified from attendance a join attendance_sessions s on s.id = a.session_id where s.class_subject_id = ?",
                rs -> { abs.computeIfAbsent(rs.getObject(1, UUID.class), k -> new ArrayList<>())
                        .add(new AbsenceEntry(rs.getObject(2, UUID.class), rs.getInt(3), rs.getBoolean(4))); }, csId);
        return sessions.findByClassSubjectIdOrderBySessionDateDesc(csId).stream().map(s -> {
            List<AbsenceEntry> list = abs.getOrDefault(s.getId(), List.of());
            return new SessionView(s.getId(), s.getSessionDate(), s.getLessons(), s.getContent(),
                    (int) list.stream().filter(x -> x.absences() > 0).count(), list);
        }).toList();
    }

    @Transactional
    public List<SessionView> createSession(AuthPrincipal p, UUID csId, SessionRequest req, ClientInfo client) {
        Ctx ctx = ctx(p, csId);
        ensureEditable(ctx);
        validateSession(ctx, req);
        if (sessions.existsByClassSubjectIdAndSessionDate(csId, req.date())) {
            throw ApiException.conflict("SESSION_EXISTS", "Já existe aula registrada nesta data. Edite o registro existente");
        }
        AttendanceSession s = new AttendanceSession();
        s.setFacultyId(ctx.facultyId());
        s.setClassSubjectId(csId);
        s.setSessionDate(req.date());
        s.setLessons(req.lessons());
        s.setContent(req.content() == null || req.content().isBlank() ? null : req.content().trim());
        s.setCreatedBy(p.userId());
        sessions.saveAndFlush(s);
        int absents = writeAbsences(ctx, s, req);
        audit.record(p, ctx.facultyId(), "ATTENDANCE_RECORDED", "CLASS_SUBJECT", csId,
                Map.of("date", req.date().toString(), "lessons", req.lessons(), "studentsWithAbsences", absents), client);
        return sessions(p, csId);
    }

    @Transactional
    public List<SessionView> updateSession(AuthPrincipal p, UUID csId, UUID sessionId, SessionRequest req, ClientInfo client) {
        Ctx ctx = ctx(p, csId);
        ensureEditable(ctx);
        AttendanceSession s = sessions.findByIdAndClassSubjectId(sessionId, csId).orElseThrow(() -> ApiException.notFound("Aula"));
        validateSession(ctx, req);
        if (!s.getSessionDate().equals(req.date()) && sessions.existsByClassSubjectIdAndSessionDate(csId, req.date())) {
            throw ApiException.conflict("SESSION_EXISTS", "Já existe aula registrada nesta data");
        }
        Map<String, Object> before = new HashMap<>();
        before.put("date", s.getSessionDate().toString());
        before.put("lessons", s.getLessons());
        jdbc.query("select student_id, absences from attendance where session_id = ? and absences > 0",
                rs -> { before.put("absences:" + rs.getString(1), rs.getInt(2)); }, sessionId);
        s.setSessionDate(req.date());
        s.setLessons(req.lessons());
        s.setContent(req.content() == null || req.content().isBlank() ? null : req.content().trim());
        jdbc.update("delete from attendance where session_id = ?", sessionId);
        int absents = writeAbsences(ctx, s, req);
        audit.record(p, ctx.facultyId(), "ATTENDANCE_CHANGED", "CLASS_SUBJECT", csId,
                Map.of("before", before, "date", req.date().toString(), "lessons", req.lessons(), "studentsWithAbsences", absents), client);
        return sessions(p, csId);
    }

    @Transactional
    public List<SessionView> deleteSession(AuthPrincipal p, UUID csId, UUID sessionId, ClientInfo client) {
        Ctx ctx = ctx(p, csId);
        ensureEditable(ctx);
        AttendanceSession s = sessions.findByIdAndClassSubjectId(sessionId, csId).orElseThrow(() -> ApiException.notFound("Aula"));
        sessions.delete(s);
        audit.record(p, ctx.facultyId(), "ATTENDANCE_DELETED", "CLASS_SUBJECT", csId, Map.of("date", s.getSessionDate().toString()), client);
        return sessions(p, csId);
    }

    private void validateSession(Ctx ctx, SessionRequest req) {
        LocalDate today = LocalDate.now(clock.withZone(ZONE));
        if (req.date().isAfter(today)) throw ApiException.badRequest("FUTURE_DATE", "Não é possível registrar frequência de aula futura");
        if (req.date().isBefore(ctx.termStart()) || req.date().isAfter(ctx.termEnd())) {
            throw ApiException.badRequest("DATE_OUTSIDE_TERM", "A data deve estar dentro do período letivo");
        }
        Set<UUID> roster = new HashSet<>(roster(ctx).stream().map(r -> (UUID) r[0]).toList());
        Set<UUID> seen = new HashSet<>();
        for (AbsenceEntry a : req.absences()) {
            if (!roster.contains(a.studentId())) throw ApiException.badRequest("STUDENT_NOT_IN_CLASS", "Aluno não pertence a esta turma");
            if (!seen.add(a.studentId())) throw ApiException.badRequest("DUPLICATE_STUDENT", "Aluno repetido na lista de frequência");
            if (a.absences() > req.lessons()) throw ApiException.badRequest("ABSENCES_ABOVE_LESSONS", "Faltas não podem exceder o número de aulas do dia");
        }
    }

    /** Grava presença de todos os alunos da turma (falta 0 para quem não foi informado). */
    private int writeAbsences(Ctx ctx, AttendanceSession s, SessionRequest req) {
        Map<UUID, AbsenceEntry> byStudent = new HashMap<>();
        req.absences().forEach(a -> byStudent.put(a.studentId(), a));
        int absents = 0;
        for (Object[] r : roster(ctx)) {
            UUID sid = (UUID) r[0];
            AbsenceEntry a = byStudent.get(sid);
            int n = a == null ? 0 : a.absences();
            if (n > 0) absents++;
            jdbc.update("insert into attendance (faculty_id, session_id, student_id, absences, justified) values (?, ?, ?, ?, ?)",
                    ctx.facultyId(), s.getId(), sid, n, a != null && a.justified());
        }
        return absents;
    }

    // ======================= Infra =======================

    UUID teacherId(AuthPrincipal p) {
        return jdbc.query("select id from teachers where user_id = ? and faculty_id = ?", rs -> rs.next() ? rs.getObject(1, UUID.class) : null,
                p.userId(), p.facultyId());
    }

    Ctx ctx(AuthPrincipal p, UUID csId) {
        UUID teacherId = teacherId(p);
        ClassSubject cs = teacherId == null ? null : classSubjects.findById(csId)
                .filter(x -> teacherId.equals(x.getTeacherId()) && x.getFacultyId().equals(p.facultyId())).orElse(null);
        if (cs == null) throw ApiException.notFound("Disciplina da turma");
        return jdbc.queryForObject("""
                select t.id, t.status, t.start_date, t.end_date from classes c join academic_terms t on t.id = c.term_id where c.id = ?""",
                (rs, i) -> new Ctx(p.facultyId(), teacherId, cs, rs.getObject(1, UUID.class), rs.getString(2),
                        rs.getObject(3, LocalDate.class), rs.getObject(4, LocalDate.class)), cs.getClassId());
    }

    /** Alunos da turma: [id, matrícula, nome, situação]. */
    List<Object[]> roster(Ctx ctx) {
        return jdbc.query("""
                select s.id, s.registration, coalesce(s.social_name, s.full_name) as name, s.status
                from class_students x join students s on s.id = x.student_id where x.class_id = ? order by name""",
                (rs, i) -> new Object[]{rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4)}, ctx.cs().getClassId());
    }

    private void ensureEditable(Ctx ctx) {
        if (!"OPEN".equals(ctx.termStatus())) {
            throw ApiException.badRequest("TERM_NOT_OPEN", "Lançamentos só são permitidos com o período letivo aberto");
        }
    }

    private void ensureNotClosed(Ctx ctx) {
        if (ctx.closed()) throw ApiException.badRequest("TERM_CLOSED", "O período letivo está encerrado");
    }

    static final String TEACHING_SQL = """
            select cs.id, c.id, c.code, co.name, t.code, t.status, s.id, s.code, s.name, s.workload_hours, cs.weekday, cs.start_time, cs.end_time, c.room,
                   (select count(*) from class_students x where x.class_id = c.id),
                   (select count(*) from attendance_sessions a where a.class_subject_id = cs.id),
                   (select count(*) from assessments a where a.class_subject_id = cs.id)
            from class_subjects cs join classes c on c.id = cs.class_id join courses co on co.id = c.course_id
            join academic_terms t on t.id = c.term_id join subjects s on s.id = cs.subject_id""";

    static TeachingView teaching(java.sql.ResultSet rs) throws java.sql.SQLException {
        Object wd = rs.getObject(11);
        return new TeachingView(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4), rs.getString(5),
                rs.getString(6), rs.getObject(7, UUID.class), rs.getString(8), rs.getString(9), rs.getInt(10),
                wd == null ? null : ((Number) wd).shortValue(), rs.getObject(12, java.time.LocalTime.class), rs.getObject(13, java.time.LocalTime.class),
                rs.getString(14), rs.getLong(15), rs.getLong(16), rs.getLong(17));
    }
}
