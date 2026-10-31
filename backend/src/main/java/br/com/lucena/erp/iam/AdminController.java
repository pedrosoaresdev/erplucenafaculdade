package br.com.lucena.erp.iam;

import br.com.lucena.erp.audit.AccessLogRepository;
import br.com.lucena.erp.audit.AuditLog;
import br.com.lucena.erp.audit.AuditLogRepository;
import br.com.lucena.erp.common.ClientInfo;
import br.com.lucena.erp.common.PageResponse;
import br.com.lucena.erp.iam.IamDtos.*;
import br.com.lucena.erp.me.MeDtos;
import br.com.lucena.erp.platform.FacultyDtos;
import br.com.lucena.erp.platform.FacultyService;
import br.com.lucena.erp.security.AuthPrincipal;
import br.com.lucena.erp.security.CurrentUser;
import br.com.lucena.erp.security.TenantResolver;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Administração da instituição. Usuários comuns operam somente na própria faculdade; o Master informa
 * ?facultyId= para operar em uma faculdade específica.
 */
@Tag(name = "Administração da instituição")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {
    private final UserAdminService usersSvc;
    private final RoleService rolesSvc;
    private final FacultyService facultySvc;
    private final TenantResolver tenant;
    private final AuditLogRepository auditRepo;
    private final AccessLogRepository accessRepo;

    private UUID fid(UUID requested) {
        return tenant.resolve(CurrentUser.get(), requested);
    }

    // ---------------- Usuários ----------------

    @GetMapping("/users")
    @PreAuthorize("hasAnyAuthority('USER_VIEW','USER_MANAGE','PLATFORM_ADMIN')")
    public PageResponse<UserView> users(@RequestParam(required = false) UUID facultyId, @RequestParam(required = false) String q,
                                        @RequestParam(required = false) User.Status status,
                                        @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return usersSvc.search(fid(facultyId), q, status, page, size);
    }

    @GetMapping("/users/{id}")
    @PreAuthorize("hasAnyAuthority('USER_VIEW','USER_MANAGE','PLATFORM_ADMIN')")
    public UserView user(@PathVariable UUID id, @RequestParam(required = false) UUID facultyId) {
        return usersSvc.get(fid(facultyId), id);
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('USER_MANAGE','PLATFORM_ADMIN')")
    public CreatedUser createUser(@RequestParam(required = false) UUID facultyId, @Valid @RequestBody CreateUser req, HttpServletRequest http) {
        return usersSvc.create(fid(facultyId), req, CurrentUser.get(), ClientInfo.from(http));
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasAnyAuthority('USER_MANAGE','PLATFORM_ADMIN')")
    public UserView updateUser(@PathVariable UUID id, @RequestParam(required = false) UUID facultyId,
                               @Valid @RequestBody UpdateUser req, HttpServletRequest http) {
        return usersSvc.update(fid(facultyId), id, req, CurrentUser.get(), ClientInfo.from(http));
    }

    @PutMapping("/users/{id}/roles")
    @PreAuthorize("hasAnyAuthority('ROLE_MANAGE','USER_MANAGE','PLATFORM_ADMIN')")
    public UserView setRoles(@PathVariable UUID id, @RequestParam(required = false) UUID facultyId,
                             @Valid @RequestBody SetRoles req, HttpServletRequest http) {
        return usersSvc.setRoles(fid(facultyId), id, req, CurrentUser.get(), ClientInfo.from(http));
    }

    @PostMapping("/users/{id}/status")
    @PreAuthorize("hasAnyAuthority('USER_MANAGE','PLATFORM_ADMIN')")
    public UserView setStatus(@PathVariable UUID id, @RequestParam(required = false) UUID facultyId,
                              @Valid @RequestBody SetStatus req, HttpServletRequest http) {
        return usersSvc.setStatus(fid(facultyId), id, req, CurrentUser.get(), ClientInfo.from(http));
    }

    @PostMapping("/users/{id}/unlock")
    @PreAuthorize("hasAnyAuthority('USER_MANAGE','PLATFORM_ADMIN')")
    public UserView unlock(@PathVariable UUID id, @RequestParam(required = false) UUID facultyId, HttpServletRequest http) {
        return usersSvc.unlock(fid(facultyId), id, CurrentUser.get(), ClientInfo.from(http));
    }

    @PostMapping("/users/{id}/reset-password")
    @PreAuthorize("hasAnyAuthority('USER_MANAGE','PLATFORM_ADMIN')")
    public TemporaryCredential resetPassword(@PathVariable UUID id, @RequestParam(required = false) UUID facultyId, HttpServletRequest http) {
        return usersSvc.resetPassword(fid(facultyId), id, CurrentUser.get(), ClientInfo.from(http));
    }

    @PostMapping("/users/{id}/reset-mfa")
    @PreAuthorize("hasAnyAuthority('MFA_RESET','PLATFORM_ADMIN')")
    public UserView resetMfa(@PathVariable UUID id, @RequestParam(required = false) UUID facultyId,
                             @Valid @RequestBody MfaReset req, HttpServletRequest http) {
        return usersSvc.resetMfa(fid(facultyId), id, req, CurrentUser.get(), ClientInfo.from(http));
    }

    @GetMapping("/users/{id}/sessions")
    @PreAuthorize("hasAnyAuthority('USER_MANAGE','PLATFORM_ADMIN')")
    public List<MeDtos.SessionView> userSessions(@PathVariable UUID id, @RequestParam(required = false) UUID facultyId) {
        return usersSvc.sessions(fid(facultyId), id);
    }

    @PostMapping("/users/{id}/sessions/revoke")
    @PreAuthorize("hasAnyAuthority('USER_MANAGE','PLATFORM_ADMIN')")
    public Map<String, Integer> revokeUserSessions(@PathVariable UUID id, @RequestParam(required = false) UUID facultyId, HttpServletRequest http) {
        return Map.of("revoked", usersSvc.revokeSessions(fid(facultyId), id, CurrentUser.get(), ClientInfo.from(http)));
    }

    @GetMapping("/users/{id}/access-history")
    @PreAuthorize("hasAnyAuthority('ACCESS_LOG_VIEW','USER_MANAGE','PLATFORM_ADMIN')")
    public PageResponse<MeDtos.AccessView> userAccess(@PathVariable UUID id, @RequestParam(required = false) UUID facultyId,
                                                      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return usersSvc.accessHistory(fid(facultyId), id, page, size);
    }

    // ---------------- Cargos e permissões ----------------

    @GetMapping("/roles")
    @PreAuthorize("hasAnyAuthority('USER_VIEW','USER_MANAGE','ROLE_MANAGE','PLATFORM_ADMIN')")
    public List<RoleView> roles(@RequestParam(required = false) UUID facultyId) {
        return rolesSvc.list(fid(facultyId));
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAnyAuthority('ROLE_MANAGE','PLATFORM_ADMIN')")
    public List<PermissionView> permissions() {
        return rolesSvc.permissionCatalog();
    }

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('ROLE_MANAGE','PLATFORM_ADMIN')")
    public RoleView createRole(@RequestParam(required = false) UUID facultyId, @Valid @RequestBody RoleRequest req, HttpServletRequest http) {
        return rolesSvc.create(fid(facultyId), req, CurrentUser.get(), ClientInfo.from(http));
    }

    @PutMapping("/roles/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_MANAGE','PLATFORM_ADMIN')")
    public RoleView updateRole(@PathVariable UUID id, @RequestParam(required = false) UUID facultyId,
                               @Valid @RequestBody RoleRequest req, HttpServletRequest http) {
        return rolesSvc.update(fid(facultyId), id, req, CurrentUser.get(), ClientInfo.from(http));
    }

    @DeleteMapping("/roles/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_MANAGE','PLATFORM_ADMIN')")
    public ResponseEntity<Void> deleteRole(@PathVariable UUID id, @RequestParam(required = false) UUID facultyId, HttpServletRequest http) {
        rolesSvc.delete(fid(facultyId), id, CurrentUser.get(), ClientInfo.from(http));
        return ResponseEntity.noContent().build();
    }

    // ---------------- Configurações da instituição ----------------

    @GetMapping("/faculty/settings")
    @PreAuthorize("hasAnyAuthority('FACULTY_SETTINGS_MANAGE','PLATFORM_ADMIN')")
    public FacultyDtos.SettingsView settings(@RequestParam(required = false) UUID facultyId) {
        return facultySvc.settings(fid(facultyId));
    }

    @PutMapping("/faculty/settings")
    @PreAuthorize("hasAnyAuthority('FACULTY_SETTINGS_MANAGE','PLATFORM_ADMIN')")
    public FacultyDtos.SettingsView updateSettings(@RequestParam(required = false) UUID facultyId,
                                                   @Valid @RequestBody FacultyDtos.SettingsRequest req, HttpServletRequest http) {
        return facultySvc.updateSettings(fid(facultyId), req, CurrentUser.get(), ClientInfo.from(http));
    }

    // ---------------- Auditoria e logs ----------------

    public record AuditView(Long id, UUID facultyId, UUID actorId, String actorName, String action, String entityType,
                            String entityId, Map<String, Object> details, String ipAddress, Instant createdAt) {
        static AuditView of(AuditLog a) {
            return new AuditView(a.getId(), a.getFacultyId(), a.getActorId(), a.getActorName(), a.getAction(), a.getEntityType(),
                    a.getEntityId(), a.getDetails(), a.getIpAddress(), a.getCreatedAt());
        }
    }

    public record AccessLogView(Long id, UUID userId, String identifier, String event, boolean success, String ipAddress,
                                String detail, Instant createdAt) {
    }

    /** Master sem facultyId = trilha global; demais usuários = somente a própria faculdade. */
    @GetMapping("/audit")
    @PreAuthorize("hasAnyAuthority('AUDIT_VIEW','PLATFORM_ADMIN')")
    public PageResponse<AuditView> audit(@RequestParam(required = false) UUID facultyId, @RequestParam(required = false) String action,
                                         @RequestParam(required = false) String entityType,
                                         @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        AuthPrincipal p = CurrentUser.get();
        UUID scope = p.platformAdmin() ? facultyId : tenant.resolve(p, facultyId);
        return PageResponse.of(auditRepo.search(scope, p.platformAdmin(), blank(action), blank(entityType),
                PageRequest.of(page, Math.min(size, 200))), AuditView::of);
    }

    @GetMapping("/access-logs")
    @PreAuthorize("hasAnyAuthority('ACCESS_LOG_VIEW','PLATFORM_ADMIN')")
    public PageResponse<AccessLogView> accessLogs(@RequestParam(required = false) UUID facultyId,
                                                  @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        AuthPrincipal p = CurrentUser.get();
        var pageable = PageRequest.of(page, Math.min(size, 200));
        var result = p.platformAdmin() && facultyId == null ? accessRepo.findAllByOrderByCreatedAtDesc(pageable)
                : accessRepo.findByFacultyIdOrderByCreatedAtDesc(tenant.resolve(p, facultyId), pageable);
        return PageResponse.of(result, a -> new AccessLogView(a.getId(), a.getUserId(), a.getIdentifier(), a.getEvent(),
                a.isSuccess(), a.getIpAddress(), a.getDetail(), a.getCreatedAt()));
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
