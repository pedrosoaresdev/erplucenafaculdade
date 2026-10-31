package br.com.lucena.erp.academic;

import br.com.lucena.erp.academic.AcademicDtos.*;
import br.com.lucena.erp.audit.AuditService;
import br.com.lucena.erp.common.ApiException;
import br.com.lucena.erp.common.ClientInfo;
import br.com.lucena.erp.common.DocumentValidator;
import br.com.lucena.erp.common.Masking;
import br.com.lucena.erp.common.PageResponse;
import br.com.lucena.erp.iam.*;
import br.com.lucena.erp.platform.PlanLimits;
import br.com.lucena.erp.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Year;
import java.time.ZoneId;
import java.util.*;

/**
 * Cadastro de alunos, professores e funcionários. O CPF completo só é retornado a quem pode alterar o cadastro;
 * listagens exibem CPF mascarado (LGPD). O login do aluno é a matrícula.
 */
@Service
@RequiredArgsConstructor
public class PeopleService {
    private final StudentRepository students;
    private final CourseRepository courses;
    private final ClassService classService;
    private final TeacherRepository teachers;
    private final EmployeeRepository employees;
    private final UserRepository users;
    private final RoleRepository roles;
    private final AccountProvisioner provisioner;
    private final AccessGuard guard;
    private final PlanLimits planLimits;
    private final AuditService audit;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    // ======================= Alunos =======================

    @Transactional(readOnly = true)
    public PageResponse<StudentView> searchStudents(UUID fid, String q, Student.Status status, UUID courseId, int page, int size,
                                                    AuthPrincipal actor) {
        String query = CatalogService.blank(q);
        if (query != null && query.replaceAll("[.\\-]", "").matches("\\d{11}")) query = query.replaceAll("\\D", "");
        boolean full = actor.has("STUDENT_MANAGE");
        return PageResponse.of(students.search(fid, query, status, courseId, PageRequest.of(page, Math.min(size, 200), Sort.by("fullName"))),
                s -> view(s, full));
    }

    @Transactional(readOnly = true)
    public StudentView getStudent(UUID fid, UUID id, AuthPrincipal actor) {
        return view(student(fid, id), actor.has("STUDENT_MANAGE"));
    }

    @Transactional
    public Created<StudentView> createStudent(UUID fid, StudentRequest req, AuthPrincipal actor, ClientInfo client) {
        String cpf = cpf(req.cpf());
        if (students.existsByFacultyIdAndCpf(fid, cpf)) throw ApiException.conflict("CPF_IN_USE", "Já existe um aluno com este CPF");
        planLimits.ensureCanAddStudent(fid);
        String registration = req.registration() == null || req.registration().isBlank() ? nextRegistration(fid) : req.registration().trim().toUpperCase();
        if (students.existsByFacultyIdAndRegistrationIgnoreCase(fid, registration)) {
            throw ApiException.conflict("REGISTRATION_IN_USE", "Matrícula já utilizada");
        }
        Course course = studentCourse(fid, req.courseId());
        Student s = new Student();
        s.setFacultyId(fid);
        s.setRegistration(registration);
        s.setCpf(cpf);
        s.setCourseId(course.getId());
        apply(s, req);
        students.saveAndFlush(s);
        String temp = null;
        if (req.createAccess()) temp = provisionStudent(fid, s);
        // Cadastro encadeado: inclusão opcional já na turma (do mesmo curso)
        if (req.classId() != null) classService.addStudent(fid, req.classId(), s.getId(), actor, client);
        audit.record(actor, fid, "STUDENT_CREATED", "STUDENT", s.getId(),
                Map.of("registration", registration, "course", course.getCode(), "accessCreated", req.createAccess()), client);
        return new Created<>(view(s, true), temp == null ? null : registration, temp);
    }

    @Transactional
    public StudentView updateStudent(UUID fid, UUID id, StudentRequest req, AuthPrincipal actor, ClientInfo client) {
        Student s = student(fid, id);
        String cpf = cpf(req.cpf());
        if (!s.getCpf().equals(cpf) && students.existsByFacultyIdAndCpf(fid, cpf)) {
            throw ApiException.conflict("CPF_IN_USE", "Já existe um aluno com este CPF");
        }
        if (req.registration() != null && !req.registration().isBlank() && !req.registration().trim().equalsIgnoreCase(s.getRegistration())) {
            throw ApiException.badRequest("REGISTRATION_IMMUTABLE", "A matrícula não pode ser alterada (é o login do aluno)");
        }
        List<String> changed = new ArrayList<>();
        if (!req.courseId().equals(s.getCourseId())) {
            Course course = studentCourse(fid, req.courseId());
            String otherClass = jdbc.query("""
                    select c.code from class_students x join classes c on c.id = x.class_id join academic_terms t on t.id = c.term_id
                    where x.student_id = ? and t.status <> 'CLOSED' and c.course_id <> ? limit 1""",
                    rs -> rs.next() ? rs.getString(1) : null, id, course.getId());
            if (otherClass != null) {
                throw ApiException.conflict("STUDENT_IN_CLASS_OF_COURSE",
                        "O aluno está na turma " + otherClass + " do curso atual. Remova-o da turma antes de trocar o curso");
            }
            s.setCourseId(course.getId());
            changed.add("course");
        }
        if (!s.getCpf().equals(cpf)) changed.add("cpf");
        if (!Objects.equals(s.getEmail(), req.email().trim().toLowerCase())) changed.add("email");
        if (!Objects.equals(s.getFullName(), req.fullName().trim())) changed.add("fullName");
        s.setCpf(cpf);
        apply(s, req);
        syncUser(s.getUserId(), s.getFullName(), s.getEmail(), s.getPhone(), true, s.getSocialName());
        audit.record(actor, fid, "STUDENT_UPDATED", "STUDENT", id, Map.of("sensitiveFieldsChanged", changed), client);
        return view(s, true);
    }

    @Transactional
    public StudentView changeStudentStatus(UUID fid, UUID id, StudentStatusRequest req, AuthPrincipal actor, ClientInfo client) {
        Student s = student(fid, id);
        Student.Status from = s.getStatus();
        if (from == req.status()) throw ApiException.badRequest("INVALID_TRANSITION", "O aluno já está nesta situação");
        s.setStatus(req.status());
        audit.record(actor, fid, "STUDENT_STATUS_CHANGED", "STUDENT", id,
                Map.of("from", from.name(), "to", req.status().name(), "reason", req.reason()), client);
        return view(s, true);
    }

    /** Gera o login do aluno (usuário = matrícula) quando não foi criado no cadastro. */
    @Transactional
    public Created<StudentView> createStudentAccess(UUID fid, UUID id, AuthPrincipal actor, ClientInfo client) {
        Student s = student(fid, id);
        if (s.getUserId() != null) throw ApiException.conflict("ACCESS_EXISTS", "O aluno já possui acesso. Use 'Redefinir senha' em Usuários");
        String temp = provisionStudent(fid, s);
        audit.record(actor, fid, "STUDENT_ACCESS_CREATED", "STUDENT", id, Map.of("registration", s.getRegistration()), client);
        return new Created<>(view(s, true), s.getRegistration(), temp);
    }

    private String provisionStudent(UUID fid, Student s) {
        if (s.getEmail() == null) throw ApiException.badRequest("EMAIL_REQUIRED", "Informe o e-mail do aluno para gerar o acesso");
        // A matrícula precisa respeitar o formato de nome de usuário
        if (!s.getRegistration().matches("^[A-Za-z0-9._-]{3,60}$")) {
            throw ApiException.badRequest("INVALID_REGISTRATION", "Matrícula incompatível com login");
        }
        var p = provisioner.create(fid, s.getRegistration(), s.getFullName(), s.getEmail(), s.getPhone(),
                Set.of(provisioner.systemRole(fid, "ALUNO")));
        p.user().setSocialName(s.getSocialName());
        s.setUserId(p.user().getId());
        return p.temporaryPassword();
    }

    /** Matrícula sequencial: AAAA + 5 dígitos (ex.: 202600001), atômica por faculdade e ano. */
    private String nextRegistration(UUID fid) {
        int year = Year.now(clock.withZone(ZoneId.of("America/Sao_Paulo"))).getValue();
        for (int attempt = 0; attempt < 1000; attempt++) {
            Integer n = jdbc.queryForObject("""
                    insert into registration_counters (faculty_id, year, last_value) values (?, ?, 1)
                    on conflict (faculty_id, year) do update set last_value = registration_counters.last_value + 1
                    returning last_value""", Integer.class, fid, year);
            String reg = year + String.format("%05d", n);
            if (!students.existsByFacultyIdAndRegistrationIgnoreCase(fid, reg) && users.findInFaculty(fid, reg).isEmpty()) return reg;
        }
        throw ApiException.conflict("REGISTRATION_EXHAUSTED", "Não foi possível gerar a matrícula");
    }

    private void apply(Student s, StudentRequest req) {
        s.setFullName(req.fullName().trim());
        s.setSocialName(CatalogService.blank(req.socialName()));
        s.setRg(CatalogService.blank(req.rg()));
        s.setBirthDate(req.birthDate());
        s.setEmail(req.email().trim().toLowerCase());
        s.setPhone(CatalogService.blank(req.phone()));
        s.setAddressStreet(CatalogService.blank(req.addressStreet()));
        s.setAddressNumber(CatalogService.blank(req.addressNumber()));
        s.setAddressDistrict(CatalogService.blank(req.addressDistrict()));
        s.setAddressCity(CatalogService.blank(req.addressCity()));
        s.setAddressState(req.addressState() == null || req.addressState().isBlank() ? null : req.addressState().toUpperCase());
        s.setAddressZip(CatalogService.blank(req.addressZip()));
    }

    private StudentView view(Student s, boolean full) {
        String currentClass = jdbc.query("""
                select c.code || ' (' || t.code || ')' from class_students cs join classes c on c.id = cs.class_id
                join academic_terms t on t.id = c.term_id where cs.student_id = ? order by t.code desc limit 1""",
                rs -> rs.next() ? rs.getString(1) : null, s.getId());
        String courseName = s.getCourseId() == null ? null : courses.findById(s.getCourseId()).map(Course::getName).orElse(null);
        return new StudentView(s.getId(), s.getRegistration(), s.getCourseId(), courseName, s.getFullName(), s.getSocialName(), Masking.cpf(s.getCpf()), full ? s.getCpf() : null,
                full ? s.getRg() : null, full ? s.getBirthDate() : null, s.getEmail(), s.getPhone(),
                full ? s.getAddressStreet() : null, full ? s.getAddressNumber() : null, full ? s.getAddressDistrict() : null,
                s.getAddressCity(), s.getAddressState(), full ? s.getAddressZip() : null,
                s.getStatus(), s.getUserId() != null, currentClass, s.getCreatedAt());
    }

    private Course studentCourse(UUID fid, UUID courseId) {
        Course c = courses.findByIdAndFacultyId(courseId, fid)
                .orElseThrow(() -> ApiException.badRequest("INVALID_COURSE", "Curso inválido para esta instituição"));
        if (!c.isActive()) throw ApiException.badRequest("COURSE_INACTIVE", "O curso está inativo");
        return c;
    }

    private Student student(UUID fid, UUID id) {
        return students.findByIdAndFacultyId(id, fid).orElseThrow(() -> ApiException.notFound("Aluno"));
    }

    // ======================= Professores =======================

    @Transactional(readOnly = true)
    public PageResponse<TeacherView> searchTeachers(UUID fid, String q, Teacher.Status status, int page, int size, AuthPrincipal actor) {
        boolean full = actor.has("TEACHER_MANAGE");
        String query = CatalogService.blank(q);
        if (query != null && query.replaceAll("[.\\-]", "").matches("\\d{11}")) query = query.replaceAll("\\D", "");
        return PageResponse.of(teachers.search(fid, query, status, PageRequest.of(page, Math.min(size, 500), Sort.by("fullName"))),
                t -> view(t, full));
    }

    @Transactional
    public Created<TeacherView> createTeacher(UUID fid, TeacherRequest req, AuthPrincipal actor, ClientInfo client) {
        String cpf = cpf(req.cpf());
        if (teachers.existsByFacultyIdAndCpf(fid, cpf)) throw ApiException.conflict("CPF_IN_USE", "Já existe um professor com este CPF");
        Teacher t = new Teacher();
        t.setFacultyId(fid);
        t.setCpf(cpf);
        apply(t, req);
        teachers.save(t);
        String temp = null;
        if (req.username() != null && !req.username().isBlank()) temp = provisionTeacher(fid, t, req.username());
        audit.record(actor, fid, "TEACHER_CREATED", "TEACHER", t.getId(), Map.of("accessCreated", temp != null), client);
        return new Created<>(view(t, true), temp == null ? null : req.username(), temp);
    }

    @Transactional
    public TeacherView updateTeacher(UUID fid, UUID id, TeacherRequest req, AuthPrincipal actor, ClientInfo client) {
        Teacher t = teacher(fid, id);
        String cpf = cpf(req.cpf());
        if (!t.getCpf().equals(cpf) && teachers.existsByFacultyIdAndCpf(fid, cpf)) {
            throw ApiException.conflict("CPF_IN_USE", "Já existe um professor com este CPF");
        }
        String from = t.getStatus().name();
        t.setCpf(cpf);
        apply(t, req);
        syncUser(t.getUserId(), t.getFullName(), t.getEmail(), t.getPhone(), false, null);
        audit.record(actor, fid, "TEACHER_UPDATED", "TEACHER", id, Map.of("statusFrom", from, "statusTo", t.getStatus().name()), client);
        return view(t, true);
    }

    @Transactional
    public Created<TeacherView> createTeacherAccess(UUID fid, UUID id, AccessRequest req, AuthPrincipal actor, ClientInfo client) {
        Teacher t = teacher(fid, id);
        if (t.getUserId() != null) throw ApiException.conflict("ACCESS_EXISTS", "O professor já possui acesso");
        if (req.username() == null || req.username().isBlank()) throw ApiException.badRequest("USERNAME_REQUIRED", "Informe o nome de usuário");
        String temp = provisionTeacher(fid, t, req.username());
        audit.record(actor, fid, "TEACHER_ACCESS_CREATED", "TEACHER", id, Map.of("username", req.username()), client);
        return new Created<>(view(t, true), req.username(), temp);
    }

    @Transactional
    public void deleteTeacher(UUID fid, UUID id, AuthPrincipal actor, ClientInfo client) {
        Teacher t = teacher(fid, id);
        if (t.getUserId() != null || count("select count(*) from class_subjects where teacher_id = ?", id) > 0
                || count("select count(*) from courses where coordinator_id = ?", id) > 0) {
            throw ApiException.conflict("TEACHER_IN_USE", "O professor possui acesso, aulas ou coordenação vinculados. Inative-o em vez de excluir");
        }
        teachers.delete(t);
        audit.record(actor, fid, "TEACHER_DELETED", "TEACHER", id, Map.of(), client);
    }

    /** O cargo Professor é concedido por regra do sistema a quem gerencia professores. */
    private String provisionTeacher(UUID fid, Teacher t, String username) {
        var p = provisioner.create(fid, username.trim(), t.getFullName(), t.getEmail(), t.getPhone(),
                Set.of(provisioner.systemRole(fid, "PROFESSOR")));
        t.setUserId(p.user().getId());
        return p.temporaryPassword();
    }

    private void apply(Teacher t, TeacherRequest req) {
        t.setFullName(req.fullName().trim());
        t.setEmail(req.email() == null || req.email().isBlank() ? null : req.email().trim().toLowerCase());
        t.setPhone(CatalogService.blank(req.phone()));
        t.setAcademicDegree(CatalogService.blank(req.academicDegree()));
        t.setStatus(req.status());
    }

    private TeacherView view(Teacher t, boolean full) {
        String username = t.getUserId() == null ? null : users.findById(t.getUserId()).map(User::getUsername).orElse(null);
        return new TeacherView(t.getId(), t.getFullName(), Masking.cpf(t.getCpf()), full ? t.getCpf() : null, t.getEmail(), t.getPhone(),
                t.getAcademicDegree(), t.getStatus(), t.getUserId() != null, username,
                count("select count(*) from class_subjects where teacher_id = ?", t.getId()));
    }

    private Teacher teacher(UUID fid, UUID id) {
        return teachers.findByIdAndFacultyId(id, fid).orElseThrow(() -> ApiException.notFound("Professor"));
    }

    // ======================= Funcionários =======================

    @Transactional(readOnly = true)
    public PageResponse<EmployeeView> searchEmployees(UUID fid, String q, Employee.Status status, int page, int size, AuthPrincipal actor) {
        boolean full = actor.has("USER_MANAGE");
        String query = CatalogService.blank(q);
        if (query != null && query.replaceAll("[.\\-]", "").matches("\\d{11}")) query = query.replaceAll("\\D", "");
        return PageResponse.of(employees.search(fid, query, status, PageRequest.of(page, Math.min(size, 200), Sort.by("fullName"))),
                e -> view(e, full));
    }

    @Transactional
    public Created<EmployeeView> createEmployee(UUID fid, EmployeeRequest req, AuthPrincipal actor, ClientInfo client) {
        String cpf = cpf(req.cpf());
        if (employees.existsByFacultyIdAndCpf(fid, cpf)) throw ApiException.conflict("CPF_IN_USE", "Já existe um funcionário com este CPF");
        Employee e = new Employee();
        e.setFacultyId(fid);
        e.setCpf(cpf);
        apply(e, req);
        employees.save(e);
        String temp = null;
        if (req.username() != null && !req.username().isBlank()) temp = provisionEmployee(fid, e, req.username(), req.roleIds(), actor);
        audit.record(actor, fid, "EMPLOYEE_CREATED", "EMPLOYEE", e.getId(), Map.of("accessCreated", temp != null), client);
        return new Created<>(view(e, true), temp == null ? null : req.username(), temp);
    }

    @Transactional
    public EmployeeView updateEmployee(UUID fid, UUID id, EmployeeRequest req, AuthPrincipal actor, ClientInfo client) {
        Employee e = employee(fid, id);
        String cpf = cpf(req.cpf());
        if (!e.getCpf().equals(cpf) && employees.existsByFacultyIdAndCpf(fid, cpf)) {
            throw ApiException.conflict("CPF_IN_USE", "Já existe um funcionário com este CPF");
        }
        e.setCpf(cpf);
        apply(e, req);
        syncUser(e.getUserId(), e.getFullName(), e.getEmail(), e.getPhone(), false, null);
        audit.record(actor, fid, "EMPLOYEE_UPDATED", "EMPLOYEE", id, Map.of("status", e.getStatus().name()), client);
        return view(e, true);
    }

    @Transactional
    public Created<EmployeeView> createEmployeeAccess(UUID fid, UUID id, AccessRequest req, AuthPrincipal actor, ClientInfo client) {
        Employee e = employee(fid, id);
        if (e.getUserId() != null) throw ApiException.conflict("ACCESS_EXISTS", "O funcionário já possui acesso");
        if (req.username() == null || req.username().isBlank()) throw ApiException.badRequest("USERNAME_REQUIRED", "Informe o nome de usuário");
        String temp = provisionEmployee(fid, e, req.username(), req.roleIds(), actor);
        audit.record(actor, fid, "EMPLOYEE_ACCESS_CREATED", "EMPLOYEE", id, Map.of("username", req.username()), client);
        return new Created<>(view(e, true), req.username(), temp);
    }

    @Transactional
    public void deleteEmployee(UUID fid, UUID id, AuthPrincipal actor, ClientInfo client) {
        Employee e = employee(fid, id);
        if (e.getUserId() != null) throw ApiException.conflict("EMPLOYEE_HAS_ACCESS", "O funcionário possui acesso. Inative-o em vez de excluir");
        employees.delete(e);
        audit.record(actor, fid, "EMPLOYEE_DELETED", "EMPLOYEE", id, Map.of(), client);
    }

    private String provisionEmployee(UUID fid, Employee e, String username, Set<UUID> roleIds, AuthPrincipal actor) {
        if (roleIds == null || roleIds.isEmpty()) throw ApiException.badRequest("ROLES_REQUIRED", "Selecione ao menos um cargo para o acesso");
        Set<Role> rs = new HashSet<>();
        for (UUID rid : roleIds) {
            rs.add(roles.findByIdAndFacultyId(rid, fid).orElseThrow(() -> ApiException.badRequest("INVALID_ROLE", "Cargo inválido para esta instituição")));
        }
        guard.ensureCanGrant(actor, rs);
        var p = provisioner.create(fid, username.trim(), e.getFullName(), e.getEmail(), e.getPhone(), rs);
        e.setUserId(p.user().getId());
        return p.temporaryPassword();
    }

    private void apply(Employee e, EmployeeRequest req) {
        e.setFullName(req.fullName().trim());
        e.setEmail(req.email() == null || req.email().isBlank() ? null : req.email().trim().toLowerCase());
        e.setPhone(CatalogService.blank(req.phone()));
        e.setDepartment(CatalogService.blank(req.department()));
        e.setJobTitle(CatalogService.blank(req.jobTitle()));
        e.setStatus(req.status());
    }

    private EmployeeView view(Employee e, boolean full) {
        String username = e.getUserId() == null ? null : users.findById(e.getUserId()).map(User::getUsername).orElse(null);
        return new EmployeeView(e.getId(), e.getFullName(), Masking.cpf(e.getCpf()), full ? e.getCpf() : null, e.getEmail(), e.getPhone(),
                e.getDepartment(), e.getJobTitle(), e.getStatus(), e.getUserId() != null, username);
    }

    private Employee employee(UUID fid, UUID id) {
        return employees.findByIdAndFacultyId(id, fid).orElseThrow(() -> ApiException.notFound("Funcionário"));
    }

    // ======================= Comum =======================

    /** Mantém nome/e-mail/telefone do usuário de acesso coerentes com o cadastro. */
    private void syncUser(UUID userId, String fullName, String email, String phone, boolean setSocialName, String socialName) {
        if (userId == null) return;
        users.findById(userId).ifPresent(u -> {
            u.setFullName(fullName);
            if (email != null) u.setEmail(email);
            u.setPhone(phone);
            if (setSocialName) u.setSocialName(socialName);
        });
    }

    private static String cpf(String raw) {
        if (!DocumentValidator.isValidCpf(raw)) throw ApiException.badRequest("INVALID_CPF", "CPF inválido");
        return DocumentValidator.digits(raw);
    }

    private long count(String sql, Object... args) {
        Long v = jdbc.queryForObject(sql, Long.class, args);
        return v == null ? 0 : v;
    }
}
