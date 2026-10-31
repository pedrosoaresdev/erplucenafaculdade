package br.com.lucena.erp.platform;

import br.com.lucena.erp.audit.AuditService;
import br.com.lucena.erp.auth.UserSessionRepository;
import br.com.lucena.erp.common.ApiException;
import br.com.lucena.erp.common.ClientInfo;
import br.com.lucena.erp.common.DocumentValidator;
import br.com.lucena.erp.common.PageResponse;
import br.com.lucena.erp.crypto.Tokens;
import br.com.lucena.erp.iam.Role;
import br.com.lucena.erp.iam.RoleRepository;
import br.com.lucena.erp.iam.User;
import br.com.lucena.erp.iam.UserRepository;
import br.com.lucena.erp.platform.FacultyDtos.*;
import br.com.lucena.erp.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.*;

@Service
@RequiredArgsConstructor
public class FacultyService {
    private final FacultyRepository faculties;
    private final FacultySettingsRepository settingsRepo;
    private final PlanRepository plans;
    private final RoleRepository roles;
    private final UserRepository users;
    private final UserSessionRepository sessions;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<FacultyView> search(String q, Faculty.Status status, int page, int size) {
        var result = faculties.search(blank(q), status, PageRequest.of(page, Math.min(size, 100), Sort.by("name")));
        return PageResponse.of(result, this::view);
    }

    @Transactional(readOnly = true)
    public FacultyView get(UUID id) {
        return view(find(id));
    }

    @Transactional
    public CreatedFaculty create(FacultyRequest req, AuthPrincipal actor, ClientInfo client) {
        String cnpj = validateCnpj(req.cnpj());
        if (faculties.existsBySlug(req.slug())) throw ApiException.conflict("SLUG_IN_USE", "Identificador (slug) já utilizado");
        if (faculties.existsByCnpj(cnpj)) throw ApiException.conflict("CNPJ_IN_USE", "CNPJ já cadastrado");
        Faculty f = new Faculty();
        apply(f, req, cnpj);
        faculties.saveAndFlush(f);

        FacultySettings s = new FacultySettings();
        s.setFacultyId(f.getId());
        settingsRepo.save(s);

        Map<String, Role> created = new HashMap<>();
        for (Role template : roles.findTemplates()) {
            Role r = new Role();
            r.setFacultyId(f.getId());
            r.setCode(template.getCode());
            r.setName(template.getName());
            r.setDescription(template.getDescription());
            r.setSystemRole(true);
            r.setPermissions(new HashSet<>(template.getPermissions()));
            created.put(r.getCode(), roles.save(r));
        }

        String tempPassword = null;
        String adminUsername = null;
        if (req.initialAdmin() != null) {
            tempPassword = Tokens.temporaryPassword();
            User u = new User();
            u.setFacultyId(f.getId());
            u.setUsername(req.initialAdmin().username());
            u.setFullName(req.initialAdmin().fullName());
            u.setEmail(req.initialAdmin().email().toLowerCase());
            u.setPasswordHash(encoder.encode(tempPassword));
            u.setMustChangePassword(true);
            u.getRoles().add(created.get("DIRETOR_GERAL"));
            users.save(u);
            adminUsername = u.getUsername();
        }
        audit.record(actor, f.getId(), "FACULTY_CREATED", "FACULTY", f.getId(),
                Map.of("slug", f.getSlug(), "initialAdmin", adminUsername == null ? "-" : adminUsername), client);
        return new CreatedFaculty(view(f), adminUsername, tempPassword);
    }

    @Transactional
    public FacultyView update(UUID id, FacultyRequest req, AuthPrincipal actor, ClientInfo client) {
        Faculty f = find(id);
        String cnpj = validateCnpj(req.cnpj());
        if (!f.getSlug().equals(req.slug()) && faculties.existsBySlug(req.slug())) {
            throw ApiException.conflict("SLUG_IN_USE", "Identificador (slug) já utilizado");
        }
        if (!f.getCnpj().equals(cnpj) && faculties.existsByCnpj(cnpj)) {
            throw ApiException.conflict("CNPJ_IN_USE", "CNPJ já cadastrado");
        }
        apply(f, req, cnpj);
        audit.record(actor, f.getId(), "FACULTY_UPDATED", "FACULTY", f.getId(), Map.of(), client);
        return view(f);
    }

    @Transactional
    public FacultyView changeStatus(UUID id, StatusChange req, AuthPrincipal actor, ClientInfo client) {
        Faculty f = find(id);
        Faculty.Status from = f.getStatus();
        Faculty.Status to = switch (req.action()) {
            case ACTIVATE, REACTIVATE -> Faculty.Status.ACTIVE;
            case SUSPEND -> Faculty.Status.SUSPENDED;
            case ARCHIVE -> Faculty.Status.ARCHIVED;
        };
        if (from == Faculty.Status.ARCHIVED && req.action() != StatusAction.REACTIVATE) {
            throw ApiException.badRequest("INVALID_TRANSITION", "Faculdade arquivada só pode ser reativada");
        }
        if (from == to) throw ApiException.badRequest("INVALID_TRANSITION", "A faculdade já está nesta situação");
        if (to != Faculty.Status.ACTIVE && (req.reason() == null || req.reason().isBlank())) {
            throw ApiException.badRequest("REASON_REQUIRED", "Informe o motivo");
        }
        f.setStatus(to);
        f.setStatusReason(req.reason());
        f.setArchivedAt(to == Faculty.Status.ARCHIVED ? clock.instant() : null);
        int revoked = 0;
        if (to != Faculty.Status.ACTIVE) {
            revoked = sessions.revokeAllForFaculty(f.getId(), clock.instant(), "FACULTY_" + to);
        }
        audit.record(actor, f.getId(), "FACULTY_STATUS_CHANGED", "FACULTY", f.getId(),
                Map.of("from", from.name(), "to", to.name(), "reason", Objects.toString(req.reason(), ""), "sessionsRevoked", revoked), client);
        return view(f);
    }

    /**
     * Exclusão física somente para faculdades sem dados (ex.: cadastro feito por engano).
     * Faculdades com usuários ou registros acadêmicos devem ser ARQUIVADAS (política de retenção).
     */
    @Transactional
    public void delete(UUID id, AuthPrincipal actor, ClientInfo client) {
        Faculty f = find(id);
        Long students = jdbc.queryForObject("select count(*) from students where faculty_id = ?", Long.class, id);
        if (users.countByFacultyId(id) > 0 || (students != null && students > 0)) {
            throw ApiException.conflict("FACULTY_HAS_DATA", "A faculdade possui dados. Utilize o arquivamento (política de retenção)");
        }
        audit.record(actor, null, "FACULTY_DELETED", "FACULTY", id, Map.of("slug", f.getSlug(), "cnpj", f.getCnpj()), client);
        jdbc.update("delete from access_logs where faculty_id = ?", id);
        faculties.delete(f);
    }

    @Transactional(readOnly = true)
    public PublicFaculty publicInfo(String slug) {
        Faculty f = faculties.findBySlug(slug).filter(x -> x.getStatus() != Faculty.Status.ARCHIVED)
                .orElseThrow(() -> ApiException.notFound("Instituição"));
        String color = settingsRepo.findById(f.getId()).map(FacultySettings::getPrimaryColor).orElse("#0B2545");
        return new PublicFaculty(f.getSlug(), f.getTradeName() != null ? f.getTradeName() : f.getName(), f.getLogoUrl(), color);
    }

    @Transactional(readOnly = true)
    public SettingsView settings(UUID facultyId) {
        FacultySettings s = settingsRepo.findById(facultyId).orElseThrow(() -> ApiException.notFound("Configuração"));
        return new SettingsView(s.getPrimaryColor(), s.getAcademicMinGrade(), s.getAcademicMinAttendancePct(), s.getLateFeePct(),
                s.getMonthlyInterestPct(), s.getSessionTimeoutMinutes(), s.getDataRetentionYears());
    }

    @Transactional
    public SettingsView updateSettings(UUID facultyId, SettingsRequest req, AuthPrincipal actor, ClientInfo client) {
        FacultySettings s = settingsRepo.findById(facultyId).orElseThrow(() -> ApiException.notFound("Configuração"));
        Map<String, Object> before = Map.of("lateFeePct", s.getLateFeePct(), "monthlyInterestPct", s.getMonthlyInterestPct(),
                "academicMinGrade", s.getAcademicMinGrade(), "academicMinAttendancePct", s.getAcademicMinAttendancePct());
        s.setPrimaryColor(req.primaryColor());
        s.setAcademicMinGrade(req.academicMinGrade());
        s.setAcademicMinAttendancePct(req.academicMinAttendancePct());
        s.setLateFeePct(req.lateFeePct());
        s.setMonthlyInterestPct(req.monthlyInterestPct());
        s.setSessionTimeoutMinutes(req.sessionTimeoutMinutes());
        s.setDataRetentionYears(req.dataRetentionYears());
        s.setUpdatedAt(clock.instant());
        audit.record(actor, facultyId, "FACULTY_SETTINGS_UPDATED", "FACULTY_SETTINGS", facultyId, Map.of("before", before), client);
        return settings(facultyId);
    }

    private void apply(Faculty f, FacultyRequest req, String cnpj) {
        f.setSlug(req.slug());
        f.setName(req.name().trim());
        f.setLegalName(req.legalName().trim());
        f.setTradeName(blank(req.tradeName()));
        f.setCnpj(cnpj);
        f.setEmail(req.email().trim().toLowerCase());
        f.setPhone(blank(req.phone()));
        f.setAddressStreet(blank(req.addressStreet()));
        f.setAddressNumber(blank(req.addressNumber()));
        f.setAddressComplement(blank(req.addressComplement()));
        f.setAddressDistrict(blank(req.addressDistrict()));
        f.setAddressCity(blank(req.addressCity()));
        f.setAddressState(blank(req.addressState()));
        f.setAddressZip(blank(req.addressZip()));
        f.setLogoUrl(blank(req.logoUrl()));
        f.setPlan(req.planId() == null ? null : plans.findById(req.planId()).orElseThrow(() -> ApiException.notFound("Plano")));
    }

    private String validateCnpj(String raw) {
        if (!DocumentValidator.isValidCnpj(raw)) throw ApiException.badRequest("INVALID_CNPJ", "CNPJ inválido");
        return DocumentValidator.digits(raw);
    }

    private Faculty find(UUID id) {
        return faculties.findById(id).orElseThrow(() -> ApiException.notFound("Faculdade"));
    }

    private FacultyView view(Faculty f) {
        long u = users.countByFacultyId(f.getId());
        Long st = jdbc.queryForObject("select count(*) from students where faculty_id = ?", Long.class, f.getId());
        Long co = jdbc.queryForObject("select count(*) from courses where faculty_id = ?", Long.class, f.getId());
        return new FacultyView(f.getId(), f.getSlug(), f.getName(), f.getLegalName(), f.getTradeName(), f.getCnpj(), f.getEmail(),
                f.getPhone(), f.getAddressStreet(), f.getAddressNumber(), f.getAddressComplement(), f.getAddressDistrict(),
                f.getAddressCity(), f.getAddressState(), f.getAddressZip(), f.getLogoUrl(), f.getStatus().name(), f.getStatusReason(),
                f.getPlan() == null ? null : f.getPlan().getId(), f.getPlan() == null ? null : f.getPlan().getName(),
                f.getCreatedAt(), u, st == null ? 0 : st, co == null ? 0 : co);
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
