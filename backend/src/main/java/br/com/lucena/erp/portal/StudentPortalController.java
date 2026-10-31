package br.com.lucena.erp.portal;

import br.com.lucena.erp.portal.PortalDtos.*;
import br.com.lucena.erp.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Portal do aluno: consultas restritas ao próprio aluno autenticado. */
@Tag(name = "Portal do aluno")
@RestController
@RequestMapping("/api/student")
@PreAuthorize("hasAuthority('STUDENT_PORTAL')")
@RequiredArgsConstructor
public class StudentPortalController {
    private final StudentPortalService service;

    @GetMapping("/overview")
    public StudentOverview overview() {
        return service.overview(CurrentUser.get());
    }

    @GetMapping("/subjects/{classSubjectId}")
    public MySubjectDetail subject(@PathVariable UUID classSubjectId) {
        return service.subject(CurrentUser.get(), classSubjectId);
    }

    @GetMapping("/history")
    public List<HistoryTerm> history() {
        return service.history(CurrentUser.get());
    }

    @GetMapping("/curriculum")
    public CurriculumProgress curriculum() {
        return service.curriculum(CurrentUser.get());
    }
}
