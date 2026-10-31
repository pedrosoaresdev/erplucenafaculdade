package br.com.lucena.erp.portal;

import br.com.lucena.erp.common.ClientInfo;
import br.com.lucena.erp.portal.PortalDtos.*;
import br.com.lucena.erp.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/** Portal do professor: somente disciplinas/turmas vinculadas ao professor autenticado. */
@Tag(name = "Portal do professor")
@RestController
@RequestMapping("/api/teacher")
@PreAuthorize("hasAuthority('TEACHER_PORTAL')")
@RequiredArgsConstructor
public class TeacherPortalController {
    private final TeacherPortalService service;
    private final DiaryExporter exporter;

    @GetMapping("/classes")
    public List<TeachingView> classes() {
        return service.myTeaching(CurrentUser.get());
    }

    @GetMapping("/class-subjects/{id}")
    public DiaryView diary(@PathVariable UUID id) {
        return service.diary(CurrentUser.get(), id);
    }

    @PutMapping("/class-subjects/{id}/plan")
    public DiaryView plan(@PathVariable UUID id, @Valid @RequestBody PlanRequest req, HttpServletRequest http) {
        return service.updatePlan(CurrentUser.get(), id, req, ClientInfo.from(http));
    }

    @PostMapping("/class-subjects/{id}/assessments")
    @PreAuthorize("hasAuthority('TEACHER_PORTAL') and hasAuthority('GRADE_MANAGE')")
    public DiaryView createAssessment(@PathVariable UUID id, @Valid @RequestBody AssessmentRequest req, HttpServletRequest http) {
        return service.createAssessment(CurrentUser.get(), id, req, ClientInfo.from(http));
    }

    @PutMapping("/class-subjects/{id}/assessments/{aid}")
    @PreAuthorize("hasAuthority('TEACHER_PORTAL') and hasAuthority('GRADE_MANAGE')")
    public DiaryView updateAssessment(@PathVariable UUID id, @PathVariable UUID aid, @Valid @RequestBody AssessmentRequest req,
                                      HttpServletRequest http) {
        return service.updateAssessment(CurrentUser.get(), id, aid, req, ClientInfo.from(http));
    }

    @DeleteMapping("/class-subjects/{id}/assessments/{aid}")
    @PreAuthorize("hasAuthority('TEACHER_PORTAL') and hasAuthority('GRADE_MANAGE')")
    public DiaryView deleteAssessment(@PathVariable UUID id, @PathVariable UUID aid, HttpServletRequest http) {
        return service.deleteAssessment(CurrentUser.get(), id, aid, ClientInfo.from(http));
    }

    @PutMapping("/class-subjects/{id}/assessments/{aid}/grades")
    @PreAuthorize("hasAuthority('TEACHER_PORTAL') and hasAuthority('GRADE_MANAGE')")
    public DiaryView grades(@PathVariable UUID id, @PathVariable UUID aid, @Valid @RequestBody GradesRequest req, HttpServletRequest http) {
        return service.saveGrades(CurrentUser.get(), id, aid, req, ClientInfo.from(http));
    }

    @GetMapping("/class-subjects/{id}/attendance")
    public List<SessionView> sessions(@PathVariable UUID id) {
        return service.sessions(CurrentUser.get(), id);
    }

    @PostMapping("/class-subjects/{id}/attendance")
    @PreAuthorize("hasAuthority('TEACHER_PORTAL') and hasAuthority('ATTENDANCE_MANAGE')")
    public List<SessionView> createSession(@PathVariable UUID id, @Valid @RequestBody SessionRequest req, HttpServletRequest http) {
        return service.createSession(CurrentUser.get(), id, req, ClientInfo.from(http));
    }

    @PutMapping("/class-subjects/{id}/attendance/{sid}")
    @PreAuthorize("hasAuthority('TEACHER_PORTAL') and hasAuthority('ATTENDANCE_MANAGE')")
    public List<SessionView> updateSession(@PathVariable UUID id, @PathVariable UUID sid, @Valid @RequestBody SessionRequest req,
                                           HttpServletRequest http) {
        return service.updateSession(CurrentUser.get(), id, sid, req, ClientInfo.from(http));
    }

    @DeleteMapping("/class-subjects/{id}/attendance/{sid}")
    @PreAuthorize("hasAuthority('TEACHER_PORTAL') and hasAuthority('ATTENDANCE_MANAGE')")
    public List<SessionView> deleteSession(@PathVariable UUID id, @PathVariable UUID sid, HttpServletRequest http) {
        return service.deleteSession(CurrentUser.get(), id, sid, ClientInfo.from(http));
    }

    @GetMapping("/class-subjects/{id}/export")
    public ResponseEntity<byte[]> export(@PathVariable UUID id, @RequestParam(defaultValue = "xlsx") String format) {
        DiaryView d = service.diary(CurrentUser.get(), id);
        String base = ("diario-" + d.teaching().classCode() + "-" + d.teaching().subjectCode()).replaceAll("[^A-Za-z0-9._-]", "_");
        boolean csv = "csv".equalsIgnoreCase(format);
        byte[] body = csv ? exporter.csv(d) : exporter.xlsx(d);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(base + (csv ? ".csv" : ".xlsx"), StandardCharsets.UTF_8).build().toString())
                .contentType(csv ? new MediaType("text", "csv", StandardCharsets.UTF_8)
                        : MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }
}
