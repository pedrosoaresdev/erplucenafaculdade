package br.com.lucena.erp.academic;

import br.com.lucena.erp.academic.AcademicDtos.*;
import br.com.lucena.erp.common.ClientInfo;
import br.com.lucena.erp.common.PageResponse;
import br.com.lucena.erp.security.CurrentUser;
import br.com.lucena.erp.security.TenantResolver;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Cursos, disciplinas, matriz curricular, períodos letivos e turmas. */
@Tag(name = "Acadêmico — cursos e turmas")
@RestController
@RequestMapping("/api/academic")
@RequiredArgsConstructor
public class AcademicController {
    private static final String VIEW = "hasAnyAuthority('COURSE_VIEW','COURSE_MANAGE','ENROLLMENT_MANAGE','PLATFORM_ADMIN')";
    private static final String MANAGE = "hasAnyAuthority('COURSE_MANAGE','PLATFORM_ADMIN')";

    private final CatalogService catalog;
    private final ClassService classes;
    private final TenantResolver tenant;

    private UUID fid(UUID requested) {
        return tenant.resolve(CurrentUser.get(), requested);
    }

    // ---------------- Cursos ----------------
    @GetMapping("/courses")
    @PreAuthorize(VIEW)
    public PageResponse<CourseView> courses(@RequestParam(required = false) UUID facultyId, @RequestParam(required = false) String q,
                                            @RequestParam(required = false) Boolean active,
                                            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return catalog.searchCourses(fid(facultyId), q, active, page, size);
    }

    @GetMapping("/courses/{id}")
    @PreAuthorize(VIEW)
    public CourseView course(@PathVariable UUID id, @RequestParam(required = false) UUID facultyId) {
        return catalog.getCourse(fid(facultyId), id);
    }

    @PostMapping("/courses")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(MANAGE)
    public CourseView createCourse(@RequestParam(required = false) UUID facultyId, @Valid @RequestBody CourseRequest req, HttpServletRequest http) {
        return catalog.createCourse(fid(facultyId), req, CurrentUser.get(), ClientInfo.from(http));
    }

    @PutMapping("/courses/{id}")
    @PreAuthorize(MANAGE)
    public CourseView updateCourse(@PathVariable UUID id, @RequestParam(required = false) UUID facultyId,
                                   @Valid @RequestBody CourseRequest req, HttpServletRequest http) {
        return catalog.updateCourse(fid(facultyId), id, req, CurrentUser.get(), ClientInfo.from(http));
    }

    @DeleteMapping("/courses/{id}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<Void> deleteCourse(@PathVariable UUID id, @RequestParam(required = false) UUID facultyId, HttpServletRequest http) {
        catalog.deleteCourse(fid(facultyId), id, CurrentUser.get(), ClientInfo.from(http));
        return ResponseEntity.noContent().build();
    }

    // ---------------- Matriz curricular ----------------
    @GetMapping("/courses/{id}/curriculum")
    @PreAuthorize(VIEW)
    public CurriculumSummary curriculum(@PathVariable UUID id, @RequestParam(required = false) UUID facultyId) {
        return catalog.curriculum(fid(facultyId), id);
    }

    @PostMapping("/courses/{id}/curriculum")
    @PreAuthorize(MANAGE)
    public CurriculumSummary addCurriculum(@PathVariable UUID id, @RequestParam(required = false) UUID facultyId,
                                           @Valid @RequestBody CurriculumRequest req, HttpServletRequest http) {
        return catalog.addToCurriculum(fid(facultyId), id, req, CurrentUser.get(), ClientInfo.from(http));
    }

    @PutMapping("/courses/{id}/curriculum/{itemId}")
    @PreAuthorize(MANAGE)
    public CurriculumSummary updateCurriculum(@PathVariable UUID id, @PathVariable UUID itemId, @RequestParam(required = false) UUID facultyId,
                                              @Valid @RequestBody CurriculumRequest req, HttpServletRequest http) {
        return catalog.updateCurriculum(fid(facultyId), id, itemId, req, CurrentUser.get(), ClientInfo.from(http));
    }

    @DeleteMapping("/courses/{id}/curriculum/{itemId}")
    @PreAuthorize(MANAGE)
    public CurriculumSummary removeCurriculum(@PathVariable UUID id, @PathVariable UUID itemId, @RequestParam(required = false) UUID facultyId,
                                              HttpServletRequest http) {
        return catalog.removeFromCurriculum(fid(facultyId), id, itemId, CurrentUser.get(), ClientInfo.from(http));
    }

    // ---------------- Disciplinas ----------------
    @GetMapping("/subjects")
    @PreAuthorize(VIEW)
    public PageResponse<SubjectView> subjects(@RequestParam(required = false) UUID facultyId, @RequestParam(required = false) String q,
                                              @RequestParam(required = false) Boolean active,
                                              @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return catalog.searchSubjects(fid(facultyId), q, active, page, size);
    }

    @PostMapping("/subjects")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(MANAGE)
    public SubjectView createSubject(@RequestParam(required = false) UUID facultyId, @Valid @RequestBody SubjectRequest req, HttpServletRequest http) {
        return catalog.createSubject(fid(facultyId), req, CurrentUser.get(), ClientInfo.from(http));
    }

    @PutMapping("/subjects/{id}")
    @PreAuthorize(MANAGE)
    public SubjectView updateSubject(@PathVariable UUID id, @RequestParam(required = false) UUID facultyId,
                                     @Valid @RequestBody SubjectRequest req, HttpServletRequest http) {
        return catalog.updateSubject(fid(facultyId), id, req, CurrentUser.get(), ClientInfo.from(http));
    }

    @DeleteMapping("/subjects/{id}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<Void> deleteSubject(@PathVariable UUID id, @RequestParam(required = false) UUID facultyId, HttpServletRequest http) {
        catalog.deleteSubject(fid(facultyId), id, CurrentUser.get(), ClientInfo.from(http));
        return ResponseEntity.noContent().build();
    }

    // ---------------- Períodos letivos ----------------
    @GetMapping("/terms")
    @PreAuthorize(VIEW)
    public List<TermView> terms(@RequestParam(required = false) UUID facultyId) {
        return catalog.terms(fid(facultyId));
    }

    @PostMapping("/terms")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(MANAGE)
    public TermView createTerm(@RequestParam(required = false) UUID facultyId, @Valid @RequestBody TermRequest req, HttpServletRequest http) {
        return catalog.createTerm(fid(facultyId), req, CurrentUser.get(), ClientInfo.from(http));
    }

    @PutMapping("/terms/{id}")
    @PreAuthorize(MANAGE)
    public TermView updateTerm(@PathVariable UUID id, @RequestParam(required = false) UUID facultyId,
                               @Valid @RequestBody TermRequest req, HttpServletRequest http) {
        return catalog.updateTerm(fid(facultyId), id, req, CurrentUser.get(), ClientInfo.from(http));
    }

    @PostMapping("/terms/{id}/status")
    @PreAuthorize(MANAGE)
    public TermView termStatus(@PathVariable UUID id, @RequestParam(required = false) UUID facultyId,
                               @Valid @RequestBody TermStatusRequest req, HttpServletRequest http) {
        return catalog.changeTermStatus(fid(facultyId), id, req.status(), CurrentUser.get(), ClientInfo.from(http));
    }

    @DeleteMapping("/terms/{id}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<Void> deleteTerm(@PathVariable UUID id, @RequestParam(required = false) UUID facultyId, HttpServletRequest http) {
        catalog.deleteTerm(fid(facultyId), id, CurrentUser.get(), ClientInfo.from(http));
        return ResponseEntity.noContent().build();
    }

    // ---------------- Turmas ----------------
    @GetMapping("/classes")
    @PreAuthorize(VIEW)
    public List<ClassView> classes(@RequestParam(required = false) UUID facultyId, @RequestParam(required = false) UUID termId,
                                   @RequestParam(required = false) UUID courseId) {
        return classes.search(fid(facultyId), termId, courseId);
    }

    @GetMapping("/classes/{id}")
    @PreAuthorize(VIEW)
    public ClassDetail classDetail(@PathVariable UUID id, @RequestParam(required = false) UUID facultyId) {
        return classes.detail(fid(facultyId), id);
    }

    @PostMapping("/classes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(MANAGE)
    public ClassView createClass(@RequestParam(required = false) UUID facultyId, @Valid @RequestBody ClassRequest req, HttpServletRequest http) {
        return classes.create(fid(facultyId), req, CurrentUser.get(), ClientInfo.from(http));
    }

    @PutMapping("/classes/{id}")
    @PreAuthorize(MANAGE)
    public ClassView updateClass(@PathVariable UUID id, @RequestParam(required = false) UUID facultyId,
                                 @Valid @RequestBody ClassRequest req, HttpServletRequest http) {
        return classes.update(fid(facultyId), id, req, CurrentUser.get(), ClientInfo.from(http));
    }

    @DeleteMapping("/classes/{id}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<Void> deleteClass(@PathVariable UUID id, @RequestParam(required = false) UUID facultyId, HttpServletRequest http) {
        classes.delete(fid(facultyId), id, CurrentUser.get(), ClientInfo.from(http));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/classes/{id}/subjects")
    @PreAuthorize(MANAGE)
    public ClassDetail addClassSubject(@PathVariable UUID id, @RequestParam(required = false) UUID facultyId,
                                       @Valid @RequestBody ClassSubjectRequest req, HttpServletRequest http) {
        return classes.addSubject(fid(facultyId), id, req, CurrentUser.get(), ClientInfo.from(http));
    }

    @PutMapping("/classes/{id}/subjects/{csId}")
    @PreAuthorize(MANAGE)
    public ClassDetail updateClassSubject(@PathVariable UUID id, @PathVariable UUID csId, @RequestParam(required = false) UUID facultyId,
                                          @Valid @RequestBody ClassSubjectRequest req, HttpServletRequest http) {
        return classes.updateSubject(fid(facultyId), id, csId, req, CurrentUser.get(), ClientInfo.from(http));
    }

    @DeleteMapping("/classes/{id}/subjects/{csId}")
    @PreAuthorize(MANAGE)
    public ClassDetail removeClassSubject(@PathVariable UUID id, @PathVariable UUID csId, @RequestParam(required = false) UUID facultyId,
                                          HttpServletRequest http) {
        return classes.removeSubject(fid(facultyId), id, csId, CurrentUser.get(), ClientInfo.from(http));
    }

    /** Alocação de alunos: Coordenação (COURSE_MANAGE) ou Secretaria (ENROLLMENT_MANAGE). */
    @PostMapping("/classes/{id}/students")
    @PreAuthorize("hasAnyAuthority('COURSE_MANAGE','ENROLLMENT_MANAGE','PLATFORM_ADMIN')")
    public ClassDetail addStudent(@PathVariable UUID id, @RequestParam(required = false) UUID facultyId,
                                  @Valid @RequestBody AddStudentRequest req, HttpServletRequest http) {
        return classes.addStudent(fid(facultyId), id, req.studentId(), CurrentUser.get(), ClientInfo.from(http));
    }

    @DeleteMapping("/classes/{id}/students/{studentId}")
    @PreAuthorize("hasAnyAuthority('COURSE_MANAGE','ENROLLMENT_MANAGE','PLATFORM_ADMIN')")
    public ClassDetail removeStudent(@PathVariable UUID id, @PathVariable UUID studentId, @RequestParam(required = false) UUID facultyId,
                                     HttpServletRequest http) {
        return classes.removeStudent(fid(facultyId), id, studentId, CurrentUser.get(), ClientInfo.from(http));
    }
}
