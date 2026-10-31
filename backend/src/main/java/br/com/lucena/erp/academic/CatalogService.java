package br.com.lucena.erp.academic;

import br.com.lucena.erp.academic.AcademicDtos.*;
import br.com.lucena.erp.audit.AuditService;
import br.com.lucena.erp.common.ApiException;
import br.com.lucena.erp.common.ClientInfo;
import br.com.lucena.erp.common.PageResponse;
import br.com.lucena.erp.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.temporal.ChronoUnit;
import java.util.*;

/** Cursos, disciplinas, matriz curricular e períodos letivos de uma faculdade. */
@Service
@RequiredArgsConstructor
public class CatalogService {
    private final CourseRepository courses;
    private final SubjectRepository subjects;
    private final CurriculumRepository curriculum;
    private final AcademicTermRepository terms;
    private final SchoolClassRepository classes;
    private final TeacherRepository teachers;
    private final AuditService audit;
    private final JdbcTemplate jdbc;

    // ======================= Cursos =======================

    @Transactional(readOnly = true)
    public PageResponse<CourseView> searchCourses(UUID fid, String q, Boolean active, int page, int size) {
        return PageResponse.of(courses.search(fid, blank(q), active, PageRequest.of(page, Math.min(size, 200), Sort.by("name"))), this::view);
    }

    @Transactional(readOnly = true)
    public CourseView getCourse(UUID fid, UUID id) {
        return view(course(fid, id));
    }

    @Transactional
    public CourseView createCourse(UUID fid, CourseRequest req, AuthPrincipal actor, ClientInfo client) {
        if (courses.existsByFacultyIdAndCodeIgnoreCase(fid, req.code().trim())) {
            throw ApiException.conflict("COURSE_CODE_IN_USE", "Já existe um curso com este código");
        }
        Course c = new Course();
        c.setFacultyId(fid);
        apply(fid, c, req);
        courses.save(c);
        audit.record(actor, fid, "COURSE_CREATED", "COURSE", c.getId(), Map.of("code", c.getCode(), "name", c.getName()), client);
        return view(c);
    }

    @Transactional
    public CourseView updateCourse(UUID fid, UUID id, CourseRequest req, AuthPrincipal actor, ClientInfo client) {
        Course c = course(fid, id);
        if (!c.getCode().equalsIgnoreCase(req.code().trim()) && courses.existsByFacultyIdAndCodeIgnoreCase(fid, req.code().trim())) {
            throw ApiException.conflict("COURSE_CODE_IN_USE", "Já existe um curso com este código");
        }
        Integer maxSemester = jdbc.queryForObject("""
                select greatest(coalesce((select max(semester) from curriculum where course_id = ?), 0),
                                coalesce((select max(semester) from classes where course_id = ?), 0))""", Integer.class, id, id);
        if (maxSemester != null && req.durationSemesters() < maxSemester) {
            throw ApiException.badRequest("DURATION_TOO_SHORT",
                    "A matriz ou as turmas do curso usam o " + maxSemester + "º semestre; a duração não pode ser menor");
        }
        Map<String, Object> before = Map.of("monthlyFee", c.getMonthlyFee(), "active", c.isActive(), "durationSemesters", c.getDurationSemesters());
        apply(fid, c, req);
        audit.record(actor, fid, "COURSE_UPDATED", "COURSE", id, Map.of("before", before), client);
        return view(c);
    }

    @Transactional
    public void deleteCourse(UUID fid, UUID id, AuthPrincipal actor, ClientInfo client) {
        Course c = course(fid, id);
        if (classes.existsByCourseId(id) || count("select count(*) from enrollments where course_id = ?", id) > 0) {
            throw ApiException.conflict("COURSE_IN_USE", "O curso possui turmas ou matrículas. Desative-o em vez de excluir");
        }
        jdbc.update("delete from curriculum where course_id = ?", id);
        courses.delete(c);
        audit.record(actor, fid, "COURSE_DELETED", "COURSE", id, Map.of("code", c.getCode()), client);
    }

    private void apply(UUID fid, Course c, CourseRequest req) {
        if (req.coordinatorId() != null) {
            Teacher t = teachers.findByIdAndFacultyId(req.coordinatorId(), fid)
                    .orElseThrow(() -> ApiException.badRequest("INVALID_COORDINATOR", "Coordenador inválido para esta instituição"));
            if (t.getStatus() != Teacher.Status.ACTIVE) throw ApiException.badRequest("INVALID_COORDINATOR", "O coordenador está inativo");
        }
        c.setCode(req.code().trim().toUpperCase());
        c.setName(req.name().trim());
        c.setDescription(blank(req.description()));
        c.setDegree(req.degree());
        c.setModality(req.modality());
        c.setDurationSemesters(req.durationSemesters());
        c.setTotalHours(req.totalHours());
        c.setMonthlyFee(req.monthlyFee());
        c.setCoordinatorId(req.coordinatorId());
        c.setActive(req.active());
    }

    private CourseView view(Course c) {
        String coordinator = c.getCoordinatorId() == null ? null : teachers.findById(c.getCoordinatorId()).map(Teacher::getFullName).orElse(null);
        return new CourseView(c.getId(), c.getCode(), c.getName(), c.getDescription(), c.getDegree(), c.getModality(), c.getDurationSemesters(),
                c.getTotalHours(), c.getMonthlyFee(), c.getCoordinatorId(), coordinator, c.isActive(),
                count("select count(*) from curriculum where course_id = ?", c.getId()),
                count("select count(*) from classes where course_id = ?", c.getId()), c.getCreatedAt());
    }

    Course course(UUID fid, UUID id) {
        return courses.findByIdAndFacultyId(id, fid).orElseThrow(() -> ApiException.notFound("Curso"));
    }

    // ======================= Disciplinas =======================

    @Transactional(readOnly = true)
    public PageResponse<SubjectView> searchSubjects(UUID fid, String q, Boolean active, int page, int size) {
        return PageResponse.of(subjects.search(fid, blank(q), active, PageRequest.of(page, Math.min(size, 500), Sort.by("name"))), SubjectView::of);
    }

    @Transactional
    public SubjectView createSubject(UUID fid, SubjectRequest req, AuthPrincipal actor, ClientInfo client) {
        if (subjects.existsByFacultyIdAndCodeIgnoreCase(fid, req.code().trim())) {
            throw ApiException.conflict("SUBJECT_CODE_IN_USE", "Já existe uma disciplina com este código");
        }
        Subject s = new Subject();
        s.setFacultyId(fid);
        apply(s, req);
        subjects.save(s);
        audit.record(actor, fid, "SUBJECT_CREATED", "SUBJECT", s.getId(), Map.of("code", s.getCode()), client);
        return SubjectView.of(s);
    }

    @Transactional
    public SubjectView updateSubject(UUID fid, UUID id, SubjectRequest req, AuthPrincipal actor, ClientInfo client) {
        Subject s = subject(fid, id);
        if (!s.getCode().equalsIgnoreCase(req.code().trim()) && subjects.existsByFacultyIdAndCodeIgnoreCase(fid, req.code().trim())) {
            throw ApiException.conflict("SUBJECT_CODE_IN_USE", "Já existe uma disciplina com este código");
        }
        apply(s, req);
        audit.record(actor, fid, "SUBJECT_UPDATED", "SUBJECT", id, Map.of("code", s.getCode()), client);
        return SubjectView.of(s);
    }

    @Transactional
    public void deleteSubject(UUID fid, UUID id, AuthPrincipal actor, ClientInfo client) {
        Subject s = subject(fid, id);
        if (curriculum.existsBySubjectId(id) || count("select count(*) from curriculum where prerequisite_subject_id = ?", id) > 0
                || count("select count(*) from class_subjects where subject_id = ?", id) > 0) {
            throw ApiException.conflict("SUBJECT_IN_USE", "A disciplina está em matrizes curriculares ou turmas. Desative-a em vez de excluir");
        }
        subjects.delete(s);
        audit.record(actor, fid, "SUBJECT_DELETED", "SUBJECT", id, Map.of("code", s.getCode()), client);
    }

    private void apply(Subject s, SubjectRequest req) {
        s.setCode(req.code().trim().toUpperCase());
        s.setName(req.name().trim());
        s.setWorkloadHours(req.workloadHours());
        s.setCredits(req.credits());
        s.setSyllabus(blank(req.syllabus()));
        s.setActive(req.active());
    }

    Subject subject(UUID fid, UUID id) {
        return subjects.findByIdAndFacultyId(id, fid).orElseThrow(() -> ApiException.notFound("Disciplina"));
    }

    // ======================= Matriz curricular =======================

    @Transactional(readOnly = true)
    public CurriculumSummary curriculum(UUID fid, UUID courseId) {
        Course c = course(fid, courseId);
        List<CurriculumItem> items = curriculum.findByCourseIdOrderBySemesterAsc(courseId);
        Set<UUID> ids = new HashSet<>();
        items.forEach(i -> {
            ids.add(i.getSubjectId());
            if (i.getPrerequisiteSubjectId() != null) ids.add(i.getPrerequisiteSubjectId());
        });
        Map<UUID, Subject> byId = new HashMap<>();
        subjects.findByFacultyIdAndIdIn(fid, ids).forEach(s -> byId.put(s.getId(), s));
        List<CurriculumView> views = items.stream().map(i -> {
            Subject s = byId.get(i.getSubjectId());
            Subject pre = i.getPrerequisiteSubjectId() == null ? null : byId.get(i.getPrerequisiteSubjectId());
            return new CurriculumView(i.getId(), s.getId(), s.getCode(), s.getName(), s.getWorkloadHours(), s.getCredits(), i.getSemester(),
                    i.isMandatory(), i.getPrerequisiteSubjectId(), pre == null ? null : pre.getName());
        }).sorted(Comparator.comparingInt(CurriculumView::semester).thenComparing(CurriculumView::subjectName)).toList();
        int hours = views.stream().mapToInt(CurriculumView::workloadHours).sum();
        int credits = views.stream().mapToInt(CurriculumView::credits).sum();
        return new CurriculumSummary(c.getId(), c.getName(), c.getDurationSemesters(), hours, credits, views);
    }

    @Transactional
    public CurriculumSummary addToCurriculum(UUID fid, UUID courseId, CurriculumRequest req, AuthPrincipal actor, ClientInfo client) {
        Course c = course(fid, courseId);
        subject(fid, req.subjectId());
        if (curriculum.findByCourseIdAndSubjectId(courseId, req.subjectId()).isPresent()) {
            throw ApiException.conflict("SUBJECT_ALREADY_IN_CURRICULUM", "A disciplina já faz parte da matriz deste curso");
        }
        CurriculumItem item = new CurriculumItem();
        item.setFacultyId(fid);
        item.setCourseId(courseId);
        item.setSubjectId(req.subjectId());
        applyCurriculum(c, item, req);
        curriculum.save(item);
        audit.record(actor, fid, "CURRICULUM_CHANGED", "COURSE", courseId,
                Map.of("operation", "ADD", "subjectId", req.subjectId().toString(), "semester", req.semester()), client);
        return curriculum(fid, courseId);
    }

    @Transactional
    public CurriculumSummary updateCurriculum(UUID fid, UUID courseId, UUID itemId, CurriculumRequest req, AuthPrincipal actor, ClientInfo client) {
        Course c = course(fid, courseId);
        CurriculumItem item = curriculum.findByIdAndCourseId(itemId, courseId).orElseThrow(() -> ApiException.notFound("Item da matriz"));
        if (!item.getSubjectId().equals(req.subjectId())) {
            throw ApiException.badRequest("SUBJECT_IMMUTABLE", "Para trocar a disciplina, remova o item e adicione outro");
        }
        Integer firstDependent = jdbc.queryForObject(
                "select min(semester) from curriculum where course_id = ? and prerequisite_subject_id = ?", Integer.class, courseId, item.getSubjectId());
        if (firstDependent != null && req.semester() >= firstDependent) {
            throw ApiException.badRequest("PREREQUISITE_ORDER",
                    "Esta disciplina é pré-requisito de outra no " + firstDependent + "º semestre; ela deve ficar em semestre anterior");
        }
        applyCurriculum(c, item, req);
        audit.record(actor, fid, "CURRICULUM_CHANGED", "COURSE", courseId,
                Map.of("operation", "UPDATE", "subjectId", req.subjectId().toString(), "semester", req.semester()), client);
        return curriculum(fid, courseId);
    }

    @Transactional
    public CurriculumSummary removeFromCurriculum(UUID fid, UUID courseId, UUID itemId, AuthPrincipal actor, ClientInfo client) {
        course(fid, courseId);
        CurriculumItem item = curriculum.findByIdAndCourseId(itemId, courseId).orElseThrow(() -> ApiException.notFound("Item da matriz"));
        if (curriculum.existsByCourseIdAndPrerequisiteSubjectId(courseId, item.getSubjectId())) {
            throw ApiException.conflict("SUBJECT_IS_PREREQUISITE", "A disciplina é pré-requisito de outra disciplina da matriz");
        }
        if (count("select count(*) from class_subjects cs join classes c on c.id = cs.class_id where c.course_id = ? and cs.subject_id = ?",
                courseId, item.getSubjectId()) > 0) {
            throw ApiException.conflict("SUBJECT_OFFERED", "A disciplina está sendo ofertada em turmas deste curso");
        }
        curriculum.delete(item);
        audit.record(actor, fid, "CURRICULUM_CHANGED", "COURSE", courseId, Map.of("operation", "REMOVE", "subjectId", item.getSubjectId().toString()), client);
        return curriculum(fid, courseId);
    }

    private void applyCurriculum(Course c, CurriculumItem item, CurriculumRequest req) {
        if (req.semester() > c.getDurationSemesters()) {
            throw ApiException.badRequest("SEMESTER_OUT_OF_RANGE", "O curso tem " + c.getDurationSemesters() + " semestres");
        }
        if (req.prerequisiteSubjectId() != null) {
            if (req.prerequisiteSubjectId().equals(item.getSubjectId())) {
                throw ApiException.badRequest("INVALID_PREREQUISITE", "Uma disciplina não pode ser pré-requisito de si mesma");
            }
            CurriculumItem pre = curriculum.findByCourseIdAndSubjectId(c.getId(), req.prerequisiteSubjectId())
                    .orElseThrow(() -> ApiException.badRequest("INVALID_PREREQUISITE", "O pré-requisito precisa estar na matriz deste curso"));
            if (pre.getSemester() >= req.semester()) {
                throw ApiException.badRequest("PREREQUISITE_ORDER", "O pré-requisito deve estar em um semestre anterior");
            }
        }
        item.setSemester(req.semester());
        item.setMandatory(req.mandatory());
        item.setPrerequisiteSubjectId(req.prerequisiteSubjectId());
    }

    // ======================= Períodos letivos =======================

    @Transactional(readOnly = true)
    public List<TermView> terms(UUID fid) {
        return terms.findByFacultyIdOrderByCodeDesc(fid).stream().map(this::view).toList();
    }

    @Transactional
    public TermView createTerm(UUID fid, TermRequest req, AuthPrincipal actor, ClientInfo client) {
        if (terms.existsByFacultyIdAndCode(fid, req.code())) throw ApiException.conflict("TERM_CODE_IN_USE", "Período letivo já cadastrado");
        AcademicTerm t = new AcademicTerm();
        t.setFacultyId(fid);
        applyTerm(t, req);
        terms.save(t);
        audit.record(actor, fid, "TERM_CREATED", "ACADEMIC_TERM", t.getId(), Map.of("code", t.getCode()), client);
        return view(t);
    }

    @Transactional
    public TermView updateTerm(UUID fid, UUID id, TermRequest req, AuthPrincipal actor, ClientInfo client) {
        AcademicTerm t = term(fid, id);
        if (t.getStatus() == AcademicTerm.Status.CLOSED) throw ApiException.badRequest("TERM_CLOSED", "Período letivo encerrado não pode ser alterado");
        if (!t.getCode().equals(req.code()) && terms.existsByFacultyIdAndCode(fid, req.code())) {
            throw ApiException.conflict("TERM_CODE_IN_USE", "Período letivo já cadastrado");
        }
        applyTerm(t, req);
        audit.record(actor, fid, "TERM_UPDATED", "ACADEMIC_TERM", id, Map.of("code", t.getCode()), client);
        return view(t);
    }

    /** Transições: PLANEJADO → ABERTO → ENCERRADO; ENCERRADO pode ser reaberto (auditado). */
    @Transactional
    public TermView changeTermStatus(UUID fid, UUID id, AcademicTerm.Status to, AuthPrincipal actor, ClientInfo client) {
        AcademicTerm t = term(fid, id);
        AcademicTerm.Status from = t.getStatus();
        boolean valid = (from == AcademicTerm.Status.PLANNED && to == AcademicTerm.Status.OPEN)
                || (from == AcademicTerm.Status.OPEN && to == AcademicTerm.Status.CLOSED)
                || (from == AcademicTerm.Status.CLOSED && to == AcademicTerm.Status.OPEN)
                || (from == AcademicTerm.Status.OPEN && to == AcademicTerm.Status.PLANNED);
        if (!valid) throw ApiException.badRequest("INVALID_TRANSITION", "Transição de situação inválida");
        t.setStatus(to);
        audit.record(actor, fid, "TERM_STATUS_CHANGED", "ACADEMIC_TERM", id, Map.of("from", from.name(), "to", to.name()), client);
        return view(t);
    }

    @Transactional
    public void deleteTerm(UUID fid, UUID id, AuthPrincipal actor, ClientInfo client) {
        AcademicTerm t = term(fid, id);
        if (classes.existsByTermId(id) || count("select count(*) from enrollments where term_id = ?", id) > 0) {
            throw ApiException.conflict("TERM_IN_USE", "O período possui turmas ou matrículas");
        }
        terms.delete(t);
        audit.record(actor, fid, "TERM_DELETED", "ACADEMIC_TERM", id, Map.of("code", t.getCode()), client);
    }

    private void applyTerm(AcademicTerm t, TermRequest req) {
        if (!req.endDate().isAfter(req.startDate())) {
            throw ApiException.badRequest("INVALID_DATES", "A data de término deve ser posterior à de início");
        }
        if (ChronoUnit.DAYS.between(req.startDate(), req.endDate()) > 366) {
            throw ApiException.badRequest("INVALID_DATES", "Um período letivo não pode passar de um ano");
        }
        t.setCode(req.code());
        t.setStartDate(req.startDate());
        t.setEndDate(req.endDate());
    }

    private TermView view(AcademicTerm t) {
        return new TermView(t.getId(), t.getCode(), t.getStartDate(), t.getEndDate(), t.getStatus(),
                count("select count(*) from classes where term_id = ?", t.getId()));
    }

    AcademicTerm term(UUID fid, UUID id) {
        return terms.findByIdAndFacultyId(id, fid).orElseThrow(() -> ApiException.notFound("Período letivo"));
    }

    private long count(String sql, Object... args) {
        Long v = jdbc.queryForObject(sql, Long.class, args);
        return v == null ? 0 : v;
    }

    static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
