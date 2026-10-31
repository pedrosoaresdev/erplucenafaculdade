package br.com.lucena.erp.academic;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class AcademicDtos {
    private AcademicDtos() {
    }

    static final String CODE = "^[A-Za-z0-9._-]{1,20}$";
    static final String CODE_MSG = "use até 20 letras, números, ponto, hífen ou sublinhado";

    // ---------------- Cursos ----------------
    public record CourseRequest(@NotBlank @Pattern(regexp = CODE, message = CODE_MSG) String code,
                                @NotBlank @Size(max = 200) String name,
                                @Size(max = 4000) String description,
                                @NotNull Course.Degree degree,
                                @NotNull Course.Modality modality,
                                @Min(1) @Max(20) int durationSemesters,
                                @Positive Integer totalHours,
                                @NotNull @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal monthlyFee,
                                UUID coordinatorId,
                                boolean active) {
    }

    public record CourseView(UUID id, String code, String name, String description, Course.Degree degree, Course.Modality modality,
                             int durationSemesters, Integer totalHours, BigDecimal monthlyFee, UUID coordinatorId,
                             String coordinatorName, boolean active, long curriculumSubjects, long classes, Instant createdAt) {
    }

    // ---------------- Disciplinas ----------------
    /**
     * Na criação a disciplina nasce dentro de um curso: courseId e semester são obrigatórios e geram o item da matriz
     * curricular. Na edição esses campos são ignorados (o vínculo com outros cursos é feito pela matriz).
     */
    public record SubjectRequest(@NotBlank @Pattern(regexp = CODE, message = CODE_MSG) String code,
                                 @NotBlank @Size(max = 200) String name,
                                 @Min(1) @Max(2000) int workloadHours,
                                 @Min(0) @Max(100) int credits,
                                 @Size(max = 10000) String syllabus,
                                 boolean active,
                                 UUID courseId,
                                 @Min(1) @Max(20) Integer semester,
                                 Boolean mandatory,
                                 UUID prerequisiteSubjectId) {
    }

    public record CourseRef(UUID id, String code, String name, int semester) {
    }

    public record SubjectView(UUID id, String code, String name, int workloadHours, int credits, String syllabus, boolean active,
                              List<CourseRef> courses) {
    }

    // ---------------- Matriz curricular ----------------
    public record CurriculumRequest(@NotNull UUID subjectId, @Min(1) @Max(20) int semester, boolean mandatory, UUID prerequisiteSubjectId) {
    }

    public record CurriculumView(UUID id, UUID subjectId, String subjectCode, String subjectName, int workloadHours, int credits,
                                 int semester, boolean mandatory, UUID prerequisiteSubjectId, String prerequisiteName) {
    }

    public record CurriculumSummary(UUID courseId, String courseName, int durationSemesters, int totalHours, int totalCredits,
                                    List<CurriculumView> items) {
    }

    // ---------------- Períodos letivos ----------------
    public record TermRequest(@NotBlank @Pattern(regexp = "^[0-9]{4}\\.[12]$", message = "formato AAAA.S (ex.: 2026.1)") String code,
                              @NotNull LocalDate startDate, @NotNull LocalDate endDate) {
    }

    public record TermStatusRequest(@NotNull AcademicTerm.Status status) {
    }

    public record TermView(UUID id, String code, LocalDate startDate, LocalDate endDate, AcademicTerm.Status status, long classes) {
    }

    // ---------------- Turmas ----------------
    public record ClassRequest(@NotNull UUID courseId, @NotNull UUID termId,
                               @NotBlank @Pattern(regexp = "^[A-Za-z0-9._-]{1,30}$", message = "até 30 letras, números, ponto, hífen ou sublinhado") String code,
                               @Min(1) @Max(20) int semester, @NotNull SchoolClass.Shift shift,
                               @Min(1) @Max(1000) int capacity, @Size(max = 40) String room, boolean active) {
    }

    public record ClassView(UUID id, UUID courseId, String courseName, UUID termId, String termCode, String code, int semester,
                            SchoolClass.Shift shift, int capacity, String room, boolean active, long students, long subjects) {
    }

    public record ClassSubjectRequest(@NotNull UUID subjectId, UUID teacherId,
                                      @Min(1) @Max(7) Short weekday, LocalTime startTime, LocalTime endTime,
                                      @Size(max = 10000) String teachingPlan) {
    }

    public record ClassSubjectView(UUID id, UUID subjectId, String subjectCode, String subjectName, int workloadHours,
                                   UUID teacherId, String teacherName, Short weekday, LocalTime startTime, LocalTime endTime,
                                   String teachingPlan) {
    }

    public record ClassStudentView(UUID studentId, String registration, String name, String status, Instant joinedAt) {
    }

    public record ClassDetail(ClassView turma, List<ClassSubjectView> subjects, List<ClassStudentView> students) {
    }

    public record AddStudentRequest(@NotNull UUID studentId) {
    }

    // ---------------- Pessoas ----------------
    public record StudentRequest(@Pattern(regexp = "^[A-Za-z0-9._-]{3,30}$", message = "matrícula com 3 a 30 letras/números") String registration,
                                 /** Curso do aluno (obrigatório). */
                                 @NotNull UUID courseId,
                                 /** Turma do mesmo curso para incluir o aluno já no cadastro (opcional). */
                                 UUID classId,
                                 @NotBlank @Size(max = 200) String fullName,
                                 @Size(max = 200) String socialName,
                                 @NotBlank String cpf,
                                 @Size(max = 20) String rg,
                                 @Past LocalDate birthDate,
                                 @NotBlank @Email @Size(max = 200) String email,
                                 @Size(max = 30) String phone,
                                 @Size(max = 200) String addressStreet,
                                 @Size(max = 20) String addressNumber,
                                 @Size(max = 100) String addressDistrict,
                                 @Size(max = 100) String addressCity,
                                 @Pattern(regexp = "^([A-Za-z]{2})?$", message = "UF com 2 letras") String addressState,
                                 @Pattern(regexp = "^(\\d{5}-?\\d{3})?$", message = "CEP inválido") String addressZip,
                                 /** true = cria o login do aluno (usuário = matrícula). */
                                 boolean createAccess) {
    }

    public record StudentStatusRequest(@NotNull Student.Status status, @NotBlank @Size(min = 3, max = 300) String reason) {
    }

    public record StudentView(UUID id, String registration, UUID courseId, String courseName, String fullName, String socialName, String cpfMasked, String cpf,
                              String rg, LocalDate birthDate, String email, String phone, String addressStreet, String addressNumber,
                              String addressDistrict, String addressCity, String addressState, String addressZip,
                              Student.Status status, boolean hasAccess, String currentClass, Instant createdAt) {
    }

    public record TeacherRequest(@NotBlank @Size(max = 200) String fullName, @NotBlank String cpf,
                                 @Email @Size(max = 200) String email, @Size(max = 30) String phone,
                                 @Size(max = 40) String academicDegree, @NotNull Teacher.Status status,
                                 /** Usuário de acesso (opcional); recebe o cargo Professor. */
                                 @Pattern(regexp = "^([A-Za-z0-9._-]{3,60})?$", message = "usuário inválido") String username) {
    }

    public record TeacherView(UUID id, String fullName, String cpfMasked, String cpf, String email, String phone, String academicDegree,
                              Teacher.Status status, boolean hasAccess, String username, long classSubjects) {
    }

    public record EmployeeRequest(@NotBlank @Size(max = 200) String fullName, @NotBlank String cpf,
                                  @Email @Size(max = 200) String email, @Size(max = 30) String phone,
                                  @Size(max = 100) String department, @Size(max = 100) String jobTitle,
                                  @NotNull Employee.Status status,
                                  @Pattern(regexp = "^([A-Za-z0-9._-]{3,60})?$", message = "usuário inválido") String username,
                                  Set<UUID> roleIds) {
    }

    public record EmployeeView(UUID id, String fullName, String cpfMasked, String cpf, String email, String phone, String department,
                               String jobTitle, Employee.Status status, boolean hasAccess, String username) {
    }

    /** Geração de acesso posterior ao cadastro. */
    public record AccessRequest(@Pattern(regexp = "^([A-Za-z0-9._-]{3,60})?$", message = "usuário inválido") String username,
                                Set<UUID> roleIds) {
    }

    public record Created<T>(T record, String username, String temporaryPassword) {
    }
}
