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

import java.util.UUID;

/** Cadastro de alunos, professores e funcionários. */
@Tag(name = "Acadêmico — pessoas")
@RestController
@RequestMapping("/api/academic")
@RequiredArgsConstructor
public class PeopleController {
    private final PeopleService people;
    private final TenantResolver tenant;

    private UUID fid(UUID requested) {
        return tenant.resolve(CurrentUser.get(), requested);
    }

    // ---------------- Alunos ----------------
    @GetMapping("/students")
    @PreAuthorize("hasAnyAuthority('STUDENT_VIEW','STUDENT_MANAGE','ENROLLMENT_MANAGE','PLATFORM_ADMIN')")
    public PageResponse<StudentView> students(@RequestParam(required = false) UUID facultyId, @RequestParam(required = false) String q,
                                              @RequestParam(required = false) Student.Status status,
                                              @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return people.searchStudents(fid(facultyId), q, status, page, size, CurrentUser.get());
    }

    @GetMapping("/students/{id}")
    @PreAuthorize("hasAnyAuthority('STUDENT_VIEW','STUDENT_MANAGE','ENROLLMENT_MANAGE','PLATFORM_ADMIN')")
    public StudentView student(@PathVariable UUID id, @RequestParam(required = false) UUID facultyId) {
        return people.getStudent(fid(facultyId), id, CurrentUser.get());
    }

    @PostMapping("/students")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('STUDENT_MANAGE','PLATFORM_ADMIN')")
    public Created<StudentView> createStudent(@RequestParam(required = false) UUID facultyId, @Valid @RequestBody StudentRequest req,
                                              HttpServletRequest http) {
        return people.createStudent(fid(facultyId), req, CurrentUser.get(), ClientInfo.from(http));
    }

    @PutMapping("/students/{id}")
    @PreAuthorize("hasAnyAuthority('STUDENT_MANAGE','PLATFORM_ADMIN')")
    public StudentView updateStudent(@PathVariable UUID id, @RequestParam(required = false) UUID facultyId,
                                     @Valid @RequestBody StudentRequest req, HttpServletRequest http) {
        return people.updateStudent(fid(facultyId), id, req, CurrentUser.get(), ClientInfo.from(http));
    }

    @PostMapping("/students/{id}/status")
    @PreAuthorize("hasAnyAuthority('STUDENT_MANAGE','ENROLLMENT_MANAGE','PLATFORM_ADMIN')")
    public StudentView studentStatus(@PathVariable UUID id, @RequestParam(required = false) UUID facultyId,
                                     @Valid @RequestBody StudentStatusRequest req, HttpServletRequest http) {
        return people.changeStudentStatus(fid(facultyId), id, req, CurrentUser.get(), ClientInfo.from(http));
    }

    @PostMapping("/students/{id}/access")
    @PreAuthorize("hasAnyAuthority('STUDENT_MANAGE','PLATFORM_ADMIN')")
    public Created<StudentView> studentAccess(@PathVariable UUID id, @RequestParam(required = false) UUID facultyId, HttpServletRequest http) {
        return people.createStudentAccess(fid(facultyId), id, CurrentUser.get(), ClientInfo.from(http));
    }

    // ---------------- Professores ----------------
    @GetMapping("/teachers")
    @PreAuthorize("hasAnyAuthority('TEACHER_MANAGE','COURSE_VIEW','COURSE_MANAGE','PLATFORM_ADMIN')")
    public PageResponse<TeacherView> teachers(@RequestParam(required = false) UUID facultyId, @RequestParam(required = false) String q,
                                              @RequestParam(required = false) Teacher.Status status,
                                              @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return people.searchTeachers(fid(facultyId), q, status, page, size, CurrentUser.get());
    }

    @PostMapping("/teachers")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('TEACHER_MANAGE','PLATFORM_ADMIN')")
    public Created<TeacherView> createTeacher(@RequestParam(required = false) UUID facultyId, @Valid @RequestBody TeacherRequest req,
                                              HttpServletRequest http) {
        return people.createTeacher(fid(facultyId), req, CurrentUser.get(), ClientInfo.from(http));
    }

    @PutMapping("/teachers/{id}")
    @PreAuthorize("hasAnyAuthority('TEACHER_MANAGE','PLATFORM_ADMIN')")
    public TeacherView updateTeacher(@PathVariable UUID id, @RequestParam(required = false) UUID facultyId,
                                     @Valid @RequestBody TeacherRequest req, HttpServletRequest http) {
        return people.updateTeacher(fid(facultyId), id, req, CurrentUser.get(), ClientInfo.from(http));
    }

    @PostMapping("/teachers/{id}/access")
    @PreAuthorize("hasAnyAuthority('TEACHER_MANAGE','PLATFORM_ADMIN')")
    public Created<TeacherView> teacherAccess(@PathVariable UUID id, @RequestParam(required = false) UUID facultyId,
                                              @Valid @RequestBody AccessRequest req, HttpServletRequest http) {
        return people.createTeacherAccess(fid(facultyId), id, req, CurrentUser.get(), ClientInfo.from(http));
    }

    @DeleteMapping("/teachers/{id}")
    @PreAuthorize("hasAnyAuthority('TEACHER_MANAGE','PLATFORM_ADMIN')")
    public ResponseEntity<Void> deleteTeacher(@PathVariable UUID id, @RequestParam(required = false) UUID facultyId, HttpServletRequest http) {
        people.deleteTeacher(fid(facultyId), id, CurrentUser.get(), ClientInfo.from(http));
        return ResponseEntity.noContent().build();
    }

    // ---------------- Funcionários ----------------
    @GetMapping("/employees")
    @PreAuthorize("hasAnyAuthority('USER_VIEW','USER_MANAGE','PLATFORM_ADMIN')")
    public PageResponse<EmployeeView> employees(@RequestParam(required = false) UUID facultyId, @RequestParam(required = false) String q,
                                                @RequestParam(required = false) Employee.Status status,
                                                @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return people.searchEmployees(fid(facultyId), q, status, page, size, CurrentUser.get());
    }

    @PostMapping("/employees")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('USER_MANAGE','PLATFORM_ADMIN')")
    public Created<EmployeeView> createEmployee(@RequestParam(required = false) UUID facultyId, @Valid @RequestBody EmployeeRequest req,
                                                HttpServletRequest http) {
        return people.createEmployee(fid(facultyId), req, CurrentUser.get(), ClientInfo.from(http));
    }

    @PutMapping("/employees/{id}")
    @PreAuthorize("hasAnyAuthority('USER_MANAGE','PLATFORM_ADMIN')")
    public EmployeeView updateEmployee(@PathVariable UUID id, @RequestParam(required = false) UUID facultyId,
                                       @Valid @RequestBody EmployeeRequest req, HttpServletRequest http) {
        return people.updateEmployee(fid(facultyId), id, req, CurrentUser.get(), ClientInfo.from(http));
    }

    @PostMapping("/employees/{id}/access")
    @PreAuthorize("hasAnyAuthority('USER_MANAGE','PLATFORM_ADMIN')")
    public Created<EmployeeView> employeeAccess(@PathVariable UUID id, @RequestParam(required = false) UUID facultyId,
                                                @Valid @RequestBody AccessRequest req, HttpServletRequest http) {
        return people.createEmployeeAccess(fid(facultyId), id, req, CurrentUser.get(), ClientInfo.from(http));
    }

    @DeleteMapping("/employees/{id}")
    @PreAuthorize("hasAnyAuthority('USER_MANAGE','PLATFORM_ADMIN')")
    public ResponseEntity<Void> deleteEmployee(@PathVariable UUID id, @RequestParam(required = false) UUID facultyId, HttpServletRequest http) {
        people.deleteEmployee(fid(facultyId), id, CurrentUser.get(), ClientInfo.from(http));
        return ResponseEntity.noContent().build();
    }
}
