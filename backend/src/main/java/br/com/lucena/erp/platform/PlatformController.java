package br.com.lucena.erp.platform;

import br.com.lucena.erp.common.ApiException;
import br.com.lucena.erp.common.ClientInfo;
import br.com.lucena.erp.common.PageResponse;
import br.com.lucena.erp.platform.FacultyDtos.*;
import br.com.lucena.erp.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Painel Master — acesso restrito a PLATFORM_ADMIN (ver SecurityConfig). */
@Tag(name = "Plataforma (Master)")
@RestController
@RequestMapping("/api/platform")
@RequiredArgsConstructor
public class PlatformController {
    private final FacultyService faculties;
    private final PlanRepository plans;
    private final PlatformDashboardService dashboard;

    @GetMapping("/dashboard")
    public PlatformDashboardService.Dashboard dashboard() {
        return dashboard.dashboard();
    }

    @GetMapping("/faculties")
    public PageResponse<FacultyView> list(@RequestParam(required = false) String q,
                                          @RequestParam(required = false) Faculty.Status status,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return faculties.search(q, status, page, size);
    }

    @GetMapping("/faculties/{id}")
    public FacultyView get(@PathVariable UUID id) {
        return faculties.get(id);
    }

    @PostMapping("/faculties")
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedFaculty create(@Valid @RequestBody FacultyRequest req, HttpServletRequest http) {
        return faculties.create(req, CurrentUser.get(), ClientInfo.from(http));
    }

    @PutMapping("/faculties/{id}")
    public FacultyView update(@PathVariable UUID id, @Valid @RequestBody FacultyRequest req, HttpServletRequest http) {
        return faculties.update(id, req, CurrentUser.get(), ClientInfo.from(http));
    }

    @PostMapping("/faculties/{id}/status")
    public FacultyView status(@PathVariable UUID id, @Valid @RequestBody StatusChange req, HttpServletRequest http) {
        return faculties.changeStatus(id, req, CurrentUser.get(), ClientInfo.from(http));
    }

    @DeleteMapping("/faculties/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, HttpServletRequest http) {
        faculties.delete(id, CurrentUser.get(), ClientInfo.from(http));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/faculties/{id}/settings")
    public SettingsView settings(@PathVariable UUID id) {
        return faculties.settings(id);
    }

    @PutMapping("/faculties/{id}/settings")
    public SettingsView updateSettings(@PathVariable UUID id, @Valid @RequestBody SettingsRequest req, HttpServletRequest http) {
        return faculties.updateSettings(id, req, CurrentUser.get(), ClientInfo.from(http));
    }

    @GetMapping("/plans")
    public List<PlanView> plans() {
        return plans.findAllByOrderByNameAsc().stream().map(PlanView::of).toList();
    }

    @PostMapping("/plans")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public PlanView createPlan(@Valid @RequestBody PlanRequest req) {
        if (plans.findByCode(req.code()).isPresent()) throw ApiException.conflict("PLAN_CODE_IN_USE", "Código de plano já existe");
        Plan p = new Plan();
        applyPlan(p, req);
        return PlanView.of(plans.save(p));
    }

    @PutMapping("/plans/{id}")
    @Transactional
    public PlanView updatePlan(@PathVariable UUID id, @Valid @RequestBody PlanRequest req) {
        Plan p = plans.findById(id).orElseThrow(() -> ApiException.notFound("Plano"));
        applyPlan(p, req);
        return PlanView.of(p);
    }

    private static void applyPlan(Plan p, PlanRequest req) {
        p.setCode(req.code());
        p.setName(req.name());
        p.setMaxStudents(req.maxStudents());
        p.setMaxUsers(req.maxUsers());
        p.setMonthlyPrice(req.monthlyPrice());
        p.setActive(req.active());
    }
}
