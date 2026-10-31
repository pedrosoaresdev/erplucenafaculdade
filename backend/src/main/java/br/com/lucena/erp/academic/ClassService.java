package br.com.lucena.erp.academic;

import br.com.lucena.erp.academic.AcademicDtos.*;
import br.com.lucena.erp.audit.AuditService;
import br.com.lucena.erp.common.ApiException;
import br.com.lucena.erp.common.ClientInfo;
import br.com.lucena.erp.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** Turmas: oferta de disciplinas com professor e horário, e alocação de alunos. */
@Service
@RequiredArgsConstructor
public class ClassService {
    private static final String[] WEEKDAYS = {"", "segunda", "terça", "quarta", "quinta", "sexta", "sábado", "domingo"};

    private final SchoolClassRepository classes;
    private final ClassSubjectRepository classSubjects;
    private final CurriculumRepository curriculum;
    private final StudentRepository students;
    private final TeacherRepository teachers;
    private final SubjectRepository subjects;
    private final CatalogService catalog;
    private final AuditService audit;
    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public List<ClassView> search(UUID fid, UUID termId, UUID courseId) {
        return classes.search(fid, termId, courseId).stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public ClassDetail detail(UUID fid, UUID id) {
        SchoolClass c = find(fid, id);
        List<ClassSubjectView> subs = classSubjects.findByClassIdOrderByWeekdayAscStartTimeAsc(id).stream().map(this::view).toList();
        List<ClassStudentView> studs = jdbc.query("""
                select s.id, s.registration, coalesce(s.social_name, s.full_name) as name, s.status, cs.joined_at
                from class_students cs join students s on s.id = cs.student_id
                where cs.class_id = ? order by name""",
                (rs, i) -> new ClassStudentView(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getTimestamp(5).toInstant()), id);
        return new ClassDetail(view(c), subs, studs);
    }

    @Transactional
    public ClassView create(UUID fid, ClassRequest req, AuthPrincipal actor, ClientInfo client) {
        Course course = catalog.course(fid, req.courseId());
        AcademicTerm term = catalog.term(fid, req.termId());
        if (!course.isActive()) throw ApiException.badRequest("COURSE_INACTIVE", "O curso está inativo");
        ensureOpen(term);
        if (classes.existsByFacultyIdAndTermIdAndCodeIgnoreCase(fid, term.getId(), req.code().trim())) {
            throw ApiException.conflict("CLASS_CODE_IN_USE", "Já existe uma turma com este código no período");
        }
        SchoolClass c = new SchoolClass();
        c.setFacultyId(fid);
        c.setCourseId(course.getId());
        c.setTermId(term.getId());
        apply(c, course, req);
        classes.save(c);
        audit.record(actor, fid, "CLASS_CREATED", "CLASS", c.getId(), Map.of("code", c.getCode(), "term", term.getCode()), client);
        return view(c);
    }

    @Transactional
    public ClassView update(UUID fid, UUID id, ClassRequest req, AuthPrincipal actor, ClientInfo client) {
        SchoolClass c = find(fid, id);
        AcademicTerm term = catalog.term(fid, c.getTermId());
        ensureOpen(term);
        if (!c.getCourseId().equals(req.courseId()) || !c.getTermId().equals(req.termId())) {
            throw ApiException.badRequest("CLASS_COURSE_TERM_IMMUTABLE", "Curso e período letivo não podem ser alterados após a criação da turma");
        }
        if (!c.getCode().equalsIgnoreCase(req.code().trim()) && classes.existsByFacultyIdAndTermIdAndCodeIgnoreCase(fid, c.getTermId(), req.code().trim())) {
            throw ApiException.conflict("CLASS_CODE_IN_USE", "Já existe uma turma com este código no período");
        }
        long enrolled = studentCount(id);
        if (req.capacity() < enrolled) {
            throw ApiException.badRequest("CAPACITY_BELOW_ENROLLED", "A turma já tem " + enrolled + " aluno(s); a capacidade não pode ser menor");
        }
        String oldRoom = c.getRoom();
        apply(c, catalog.course(fid, c.getCourseId()), req);
        if (c.getRoom() != null && !c.getRoom().equalsIgnoreCase(Objects.toString(oldRoom, ""))) {
            for (ClassSubject cs : classSubjects.findByClassIdOrderByWeekdayAscStartTimeAsc(id)) checkConflicts(fid, c, cs.getTeacherId(), cs.getWeekday(), cs, cs.getId());
        }
        audit.record(actor, fid, "CLASS_UPDATED", "CLASS", id, Map.of("code", c.getCode(), "capacity", c.getCapacity()), client);
        return view(c);
    }

    @Transactional
    public void delete(UUID fid, UUID id, AuthPrincipal actor, ClientInfo client) {
        SchoolClass c = find(fid, id);
        if (studentCount(id) > 0 || count("select count(*) from enrollments where class_id = ?", id) > 0) {
            throw ApiException.conflict("CLASS_IN_USE", "A turma possui alunos ou matrículas. Remova os alunos ou desative a turma");
        }
        try {
            jdbc.update("delete from class_subjects where class_id = ?", id);
            classes.delete(c);
            classes.flush();
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("CLASS_IN_USE", "A turma possui registros acadêmicos vinculados e não pode ser excluída");
        }
        audit.record(actor, fid, "CLASS_DELETED", "CLASS", id, Map.of("code", c.getCode()), client);
    }

    private void apply(SchoolClass c, Course course, ClassRequest req) {
        if (req.semester() > course.getDurationSemesters()) {
            throw ApiException.badRequest("SEMESTER_OUT_OF_RANGE", "O curso tem " + course.getDurationSemesters() + " semestres");
        }
        c.setCode(req.code().trim().toUpperCase());
        c.setSemester(req.semester());
        c.setShift(req.shift());
        c.setCapacity(req.capacity());
        c.setRoom(CatalogService.blank(req.room()));
        c.setActive(req.active());
    }

    // ======================= Disciplinas da turma =======================

    @Transactional
    public ClassDetail addSubject(UUID fid, UUID classId, ClassSubjectRequest req, AuthPrincipal actor, ClientInfo client) {
        SchoolClass c = find(fid, classId);
        ensureOpen(catalog.term(fid, c.getTermId()));
        Subject s = catalog.subject(fid, req.subjectId());
        if (curriculum.findByCourseIdAndSubjectId(c.getCourseId(), s.getId()).isEmpty()) {
            throw ApiException.badRequest("SUBJECT_NOT_IN_CURRICULUM", "A disciplina não faz parte da matriz curricular do curso da turma");
        }
        if (classSubjects.existsByClassIdAndSubjectId(classId, s.getId())) {
            throw ApiException.conflict("SUBJECT_ALREADY_OFFERED", "A disciplina já está ofertada nesta turma");
        }
        ClassSubject cs = new ClassSubject();
        cs.setFacultyId(fid);
        cs.setClassId(classId);
        cs.setSubjectId(s.getId());
        applySubject(fid, c, cs, req, null);
        classSubjects.save(cs);
        audit.record(actor, fid, "CLASS_SUBJECT_ADDED", "CLASS", classId, offerDetails(s, cs), client);
        return detail(fid, classId);
    }

    @Transactional
    public ClassDetail updateSubject(UUID fid, UUID classId, UUID csId, ClassSubjectRequest req, AuthPrincipal actor, ClientInfo client) {
        SchoolClass c = find(fid, classId);
        ensureOpen(catalog.term(fid, c.getTermId()));
        ClassSubject cs = classSubjects.findByIdAndClassId(csId, classId).orElseThrow(() -> ApiException.notFound("Disciplina da turma"));
        if (!cs.getSubjectId().equals(req.subjectId())) {
            throw ApiException.badRequest("SUBJECT_IMMUTABLE", "Para trocar a disciplina, remova a oferta e adicione outra");
        }
        UUID oldTeacher = cs.getTeacherId();
        applySubject(fid, c, cs, req, csId);
        Map<String, Object> details = new HashMap<>(offerDetails(catalog.subject(fid, cs.getSubjectId()), cs));
        details.put("previousTeacherId", Objects.toString(oldTeacher, ""));
        audit.record(actor, fid, "CLASS_SUBJECT_UPDATED", "CLASS", classId, details, client);
        return detail(fid, classId);
    }

    @Transactional
    public ClassDetail removeSubject(UUID fid, UUID classId, UUID csId, AuthPrincipal actor, ClientInfo client) {
        SchoolClass c = find(fid, classId);
        ensureOpen(catalog.term(fid, c.getTermId()));
        ClassSubject cs = classSubjects.findByIdAndClassId(csId, classId).orElseThrow(() -> ApiException.notFound("Disciplina da turma"));
        if (count("select (select count(*) from assessments where class_subject_id = ?) + (select count(*) from attendance_sessions where class_subject_id = ?)"
                + " + (select count(*) from enrollment_subjects where class_subject_id = ?)", csId, csId, csId) > 0) {
            throw ApiException.conflict("CLASS_SUBJECT_IN_USE", "Já existem avaliações, frequência ou matrículas nesta disciplina da turma");
        }
        classSubjects.delete(cs);
        audit.record(actor, fid, "CLASS_SUBJECT_REMOVED", "CLASS", classId, Map.of("subjectId", cs.getSubjectId().toString()), client);
        return detail(fid, classId);
    }

    private void applySubject(UUID fid, SchoolClass c, ClassSubject cs, ClassSubjectRequest req, UUID ignoreId) {
        boolean anyTime = req.weekday() != null || req.startTime() != null || req.endTime() != null;
        boolean allTime = req.weekday() != null && req.startTime() != null && req.endTime() != null;
        if (anyTime && !allTime) throw ApiException.badRequest("INCOMPLETE_SCHEDULE", "Informe dia da semana, início e término do horário");
        if (allTime && !req.endTime().isAfter(req.startTime())) {
            throw ApiException.badRequest("INVALID_SCHEDULE", "O término da aula deve ser posterior ao início");
        }
        if (req.teacherId() != null) {
            Teacher t = teachers.findByIdAndFacultyId(req.teacherId(), fid)
                    .orElseThrow(() -> ApiException.badRequest("INVALID_TEACHER", "Professor inválido para esta instituição"));
            if (t.getStatus() != Teacher.Status.ACTIVE && !req.teacherId().equals(cs.getTeacherId())) {
                throw ApiException.badRequest("TEACHER_INACTIVE", "O professor está inativo");
            }
        }
        cs.setTeacherId(req.teacherId());
        cs.setWeekday(req.weekday());
        cs.setStartTime(req.startTime());
        cs.setEndTime(req.endTime());
        cs.setTeachingPlan(CatalogService.blank(req.teachingPlan()));
        if (allTime) checkConflicts(fid, c, req.teacherId(), req.weekday(), cs, ignoreId);
    }

    /** Impede choque de horário do professor e da sala no mesmo período letivo. */
    private void checkConflicts(UUID fid, SchoolClass c, UUID teacherId, Short weekday, ClassSubject cs, UUID ignoreId) {
        if (weekday == null || cs.getStartTime() == null) return;
        String own = jdbc.query("""
                select s.name from class_subjects x join subjects s on s.id = x.subject_id
                where x.class_id = ? and x.weekday = ? and x.start_time < ? and x.end_time > ? and (?::uuid is null or x.id <> ?::uuid)""",
                rs -> rs.next() ? rs.getString(1) : null, c.getId(), weekday, cs.getEndTime(), cs.getStartTime(), ignoreId, ignoreId);
        if (own != null) {
            throw ApiException.conflict("CLASS_SCHEDULE_CONFLICT", "A turma já tem aula de " + own + " na " + WEEKDAYS[weekday] + " neste horário");
        }
        if (teacherId != null) {
            var conflicts = classSubjects.teacherConflicts(c.getTermId(), teacherId, weekday, cs.getStartTime(), cs.getEndTime(), ignoreId);
            if (!conflicts.isEmpty()) {
                throw ApiException.conflict("TEACHER_SCHEDULE_CONFLICT", "O professor já tem aula na " + WEEKDAYS[weekday] + " neste horário ("
                        + describe(conflicts.get(0)) + ")");
            }
        }
        if (c.getRoom() != null) {
            var conflicts = classSubjects.roomConflicts(fid, c.getTermId(), c.getRoom(), weekday, cs.getStartTime(), cs.getEndTime(), ignoreId);
            if (!conflicts.isEmpty()) {
                throw ApiException.conflict("ROOM_SCHEDULE_CONFLICT", "A sala " + c.getRoom() + " já está ocupada na " + WEEKDAYS[weekday]
                        + " neste horário (" + describe(conflicts.get(0)) + ")");
            }
        }
    }

    private String describe(ClassSubject other) {
        String cls = classes.findById(other.getClassId()).map(SchoolClass::getCode).orElse("?");
        String subj = subjects.findById(other.getSubjectId()).map(Subject::getName).orElse("?");
        return "turma " + cls + ", " + subj + ", " + other.getStartTime() + "–" + other.getEndTime();
    }

    private static Map<String, Object> offerDetails(Subject s, ClassSubject cs) {
        Map<String, Object> m = new HashMap<>();
        m.put("subject", s.getCode());
        m.put("teacherId", Objects.toString(cs.getTeacherId(), ""));
        m.put("weekday", cs.getWeekday() == null ? "" : cs.getWeekday());
        m.put("start", Objects.toString(cs.getStartTime(), ""));
        m.put("end", Objects.toString(cs.getEndTime(), ""));
        return m;
    }

    // ======================= Alunos da turma =======================

    @Transactional
    public ClassDetail addStudent(UUID fid, UUID classId, UUID studentId, AuthPrincipal actor, ClientInfo client) {
        // Bloqueia a turma: verificação de capacidade consistente sob concorrência
        jdbc.queryForObject("select id from classes where id = ? and faculty_id = ? for update", UUID.class, classId, fid);
        SchoolClass c = find(fid, classId);
        ensureOpen(catalog.term(fid, c.getTermId()));
        if (!c.isActive()) throw ApiException.badRequest("CLASS_INACTIVE", "A turma está inativa");
        Student s = students.findByIdAndFacultyId(studentId, fid).orElseThrow(() -> ApiException.notFound("Aluno"));
        if (s.getStatus() != Student.Status.ACTIVE) throw ApiException.badRequest("STUDENT_NOT_ACTIVE", "Somente alunos com situação ativa podem ser incluídos");
        long enrolled = studentCount(classId);
        if (enrolled >= c.getCapacity()) throw ApiException.conflict("CLASS_FULL", "A turma atingiu a capacidade de " + c.getCapacity() + " alunos");
        String other = jdbc.query("""
                select c.code from class_students cs join classes c on c.id = cs.class_id where cs.student_id = ? and cs.term_id = ?""",
                rs -> rs.next() ? rs.getString(1) : null, studentId, c.getTermId());
        if (other != null) {
            throw ApiException.conflict("STUDENT_ALREADY_IN_TERM", "O aluno já está na turma " + other + " neste período letivo");
        }
        jdbc.update("insert into class_students (class_id, student_id, faculty_id, term_id) values (?, ?, ?, ?)", classId, studentId, fid, c.getTermId());
        audit.record(actor, fid, "CLASS_STUDENT_ADDED", "CLASS", classId, Map.of("studentId", studentId.toString(), "registration", s.getRegistration()), client);
        return detail(fid, classId);
    }

    @Transactional
    public ClassDetail removeStudent(UUID fid, UUID classId, UUID studentId, AuthPrincipal actor, ClientInfo client) {
        SchoolClass c = find(fid, classId);
        ensureOpen(catalog.term(fid, c.getTermId()));
        int n = jdbc.update("delete from class_students where class_id = ? and student_id = ? and faculty_id = ?", classId, studentId, fid);
        if (n == 0) throw ApiException.notFound("Aluno na turma");
        audit.record(actor, fid, "CLASS_STUDENT_REMOVED", "CLASS", classId, Map.of("studentId", studentId.toString()), client);
        return detail(fid, classId);
    }

    private void ensureOpen(AcademicTerm t) {
        if (t.getStatus() == AcademicTerm.Status.CLOSED) {
            throw ApiException.badRequest("TERM_CLOSED", "O período letivo " + t.getCode() + " está encerrado");
        }
    }

    private ClassView view(SchoolClass c) {
        String course = jdbc.queryForObject("select name from courses where id = ?", String.class, c.getCourseId());
        String term = jdbc.queryForObject("select code from academic_terms where id = ?", String.class, c.getTermId());
        return new ClassView(c.getId(), c.getCourseId(), course, c.getTermId(), term, c.getCode(), c.getSemester(), c.getShift(),
                c.getCapacity(), c.getRoom(), c.isActive(), studentCount(c.getId()),
                count("select count(*) from class_subjects where class_id = ?", c.getId()));
    }

    private ClassSubjectView view(ClassSubject cs) {
        Subject s = subjects.findById(cs.getSubjectId()).orElseThrow();
        String teacher = cs.getTeacherId() == null ? null : teachers.findById(cs.getTeacherId()).map(Teacher::getFullName).orElse(null);
        return new ClassSubjectView(cs.getId(), s.getId(), s.getCode(), s.getName(), s.getWorkloadHours(), cs.getTeacherId(), teacher,
                cs.getWeekday(), cs.getStartTime(), cs.getEndTime(), cs.getTeachingPlan());
    }

    private SchoolClass find(UUID fid, UUID id) {
        return classes.findByIdAndFacultyId(id, fid).orElseThrow(() -> ApiException.notFound("Turma"));
    }

    private long studentCount(UUID classId) {
        return count("select count(*) from class_students where class_id = ?", classId);
    }

    private long count(String sql, Object... args) {
        Long v = jdbc.queryForObject(sql, Long.class, args);
        return v == null ? 0 : v;
    }
}
