package br.com.lucena.erp.portal;

import br.com.lucena.erp.academic.Assessment;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class PortalDtos {
    private PortalDtos() {
    }

    // ---------------- Professor ----------------
    public record TeachingView(UUID classSubjectId, UUID classId, String classCode, String courseName, String termCode, String termStatus,
                               UUID subjectId, String subjectCode, String subjectName, int workloadHours, Short weekday,
                               LocalTime startTime, LocalTime endTime, String room, long students, long sessions, long assessments) {
    }

    public record AssessmentRequest(@NotBlank @Size(max = 120) String title, @NotNull Assessment.Kind kind,
                                    @NotNull @DecimalMin(value = "0.01") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal weight,
                                    @NotNull @DecimalMin(value = "0.01") @DecimalMax("1000") @Digits(integer = 4, fraction = 2) BigDecimal maxScore,
                                    LocalDate dueDate) {
    }

    public record AssessmentView(UUID id, String title, Assessment.Kind kind, BigDecimal weight, BigDecimal maxScore, LocalDate dueDate,
                                 long graded) {
    }

    public record GradeEntry(@NotNull UUID studentId, @DecimalMin("0") @Digits(integer = 4, fraction = 2) BigDecimal score) {
    }

    public record GradesRequest(@NotNull @Size(max = 500) List<@Valid GradeEntry> grades) {
    }

    public record StudentRow(UUID studentId, String registration, String name, String status,
                             java.util.Map<UUID, BigDecimal> scores, GradeCalculator.Result result) {
    }

    public record DiaryView(TeachingView teaching, String teachingPlan, String syllabus, BigDecimal minGrade, BigDecimal minAttendancePct,
                            List<AssessmentView> assessments, List<StudentRow> students, boolean editable) {
    }

    public record AbsenceEntry(@NotNull UUID studentId, @Min(0) @Max(10) int absences, boolean justified) {
    }

    public record SessionRequest(@NotNull LocalDate date, @Min(1) @Max(10) short lessons, @Size(max = 5000) String content,
                                 @NotNull @Size(max = 500) List<@Valid AbsenceEntry> absences) {
    }

    public record SessionView(UUID id, LocalDate date, short lessons, String content, int absentStudents,
                              List<AbsenceEntry> absences) {
    }

    public record PlanRequest(@Size(max = 20000) String teachingPlan) {
    }

    // ---------------- Aluno ----------------
    public record ScheduleItem(UUID classSubjectId, String subjectName, String teacherName, Short weekday, LocalTime startTime,
                               LocalTime endTime, String room) {
    }

    public record SubjectSummary(UUID classSubjectId, String subjectCode, String subjectName, String teacherName, int workloadHours,
                                 GradeCalculator.Result result) {
    }

    public record StudentOverview(UUID studentId, String registration, String name, String status, String courseName, String classCode,
                                  String termCode, String termStatus, Integer semester, BigDecimal minGrade, BigDecimal minAttendancePct,
                                  List<ScheduleItem> schedule, List<SubjectSummary> subjects) {
    }

    public record MyAssessment(String title, Assessment.Kind kind, BigDecimal weight, BigDecimal maxScore, LocalDate dueDate, BigDecimal score) {
    }

    public record MyAttendance(LocalDate date, short lessons, String content, int absences, boolean justified) {
    }

    public record MySubjectDetail(SubjectSummary summary, String teachingPlan, String syllabus, List<MyAssessment> assessments,
                                  List<MyAttendance> attendance) {
    }

    public record HistoryTerm(String termCode, String termStatus, String classCode, String courseName, List<SubjectSummary> subjects) {
    }

    public record CurriculumProgressItem(String subjectCode, String subjectName, int semester, boolean mandatory, int workloadHours,
                                         int credits, String status) {
    }

    public record CurriculumProgress(String courseName, int totalCredits, int completedCredits, int totalHours, int completedHours,
                                     BigDecimal completionPct, List<CurriculumProgressItem> items) {
    }
}
