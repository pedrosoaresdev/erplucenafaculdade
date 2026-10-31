package br.com.lucena.erp.iam;

import br.com.lucena.erp.audit.AccessLogRepository;
import br.com.lucena.erp.audit.AuditService;
import br.com.lucena.erp.auth.*;
import br.com.lucena.erp.common.ApiException;
import br.com.lucena.erp.common.ClientInfo;
import br.com.lucena.erp.common.PageResponse;
import br.com.lucena.erp.crypto.Tokens;
import br.com.lucena.erp.iam.IamDtos.*;
import br.com.lucena.erp.me.MeDtos;
import br.com.lucena.erp.notification.NotificationService;
import br.com.lucena.erp.platform.FacultyRepository;
import br.com.lucena.erp.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.*;

/** Gestão de usuários de UMA faculdade (facultyId já resolvido pelo TenantResolver). */
@Service
@RequiredArgsConstructor
public class UserAdminService {
    private final UserRepository users;
    private final RoleRepository roles;
    private final FacultyRepository faculties;
    private final PasswordEncoder encoder;
    private final AccessGuard guard;
    private final AccountProvisioner provisioner;
    private final MfaService mfa;
    private final RecoveryCodeService recoveryCodes;
    private final SessionService sessions;
    private final UserSessionRepository sessionRepo;
    private final AccessLogRepository accessLogs;
    private final AuditService audit;
    private final NotificationService notifications;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<UserView> search(UUID facultyId, String q, User.Status status, int page, int size) {
        var result = users.search(facultyId, q == null || q.isBlank() ? null : q.trim(), status,
                PageRequest.of(page, Math.min(size, 100), Sort.by("fullName")));
        return PageResponse.of(result, u -> UserView.of(u, clock.instant()));
    }

    @Transactional(readOnly = true)
    public UserView get(UUID facultyId, UUID id) {
        return UserView.of(find(facultyId, id), clock.instant());
    }

    @Transactional
    public CreatedUser create(UUID facultyId, CreateUser req, AuthPrincipal actor, ClientInfo client) {
        faculties.findById(facultyId).orElseThrow(() -> ApiException.notFound("Faculdade"));
        Set<Role> rs = loadRoles(facultyId, req.roleIds());
        guard.ensureCanGrant(actor, rs);
        AccountProvisioner.Provisioned p = provisioner.create(facultyId, req.username(), req.fullName(), req.email(), req.phone(), rs);
        User u = p.user();
        audit.record(actor, facultyId, "USER_CREATED", "USER", u.getId(),
                Map.of("username", u.getUsername(), "roles", rs.stream().map(Role::getCode).sorted().toList()), client);
        return new CreatedUser(UserView.of(u, clock.instant()), p.temporaryPassword());
    }

    @Transactional
    public UserView update(UUID facultyId, UUID id, UpdateUser req, AuthPrincipal actor, ClientInfo client) {
        User u = find(facultyId, id);
        u.setFullName(req.fullName().trim());
        u.setEmail(req.email().trim().toLowerCase());
        u.setPhone(req.phone());
        audit.record(actor, facultyId, "USER_UPDATED", "USER", id, Map.of(), client);
        return UserView.of(u, clock.instant());
    }

    @Transactional
    public UserView setRoles(UUID facultyId, UUID id, SetRoles req, AuthPrincipal actor, ClientInfo client) {
        User u = find(facultyId, id);
        if (u.getId().equals(actor.userId())) throw ApiException.forbidden("Você não pode alterar os próprios cargos");
        Set<Role> rs = loadRoles(facultyId, req.roleIds());
        guard.ensureCanGrant(actor, rs);
        guard.ensureCanGrant(actor, u.getRoles()); // também não pode remover cargos acima do seu nível
        List<String> before = u.getRoles().stream().map(Role::getCode).sorted().toList();
        u.setRoles(rs);
        audit.record(actor, facultyId, "USER_ROLES_CHANGED", "USER", id,
                Map.of("before", before, "after", rs.stream().map(Role::getCode).sorted().toList()), client);
        return UserView.of(u, clock.instant());
    }

    @Transactional
    public UserView setStatus(UUID facultyId, UUID id, SetStatus req, AuthPrincipal actor, ClientInfo client) {
        User u = find(facultyId, id);
        if (u.getId().equals(actor.userId())) throw ApiException.forbidden("Você não pode alterar a própria situação");
        guard.ensureCanGrant(actor, u.getRoles());
        String from = u.getStatus().name();
        u.setStatus(req.status());
        int revoked = 0;
        if (req.status() != User.Status.ACTIVE) revoked = sessions.revokeAll(u.getId(), null, "USER_" + req.status());
        audit.record(actor, facultyId, "USER_STATUS_CHANGED", "USER", id,
                Map.of("from", from, "to", req.status().name(), "reason", Objects.toString(req.reason(), ""), "sessionsRevoked", revoked), client);
        return UserView.of(u, clock.instant());
    }

    @Transactional
    public UserView unlock(UUID facultyId, UUID id, AuthPrincipal actor, ClientInfo client) {
        User u = find(facultyId, id);
        u.setLockedUntil(null);
        u.setFailedLoginCount(0);
        audit.record(actor, facultyId, "USER_UNLOCKED", "USER", id, Map.of(), client);
        return UserView.of(u, clock.instant());
    }

    /** Gera senha temporária (exibida uma vez); exige troca no próximo acesso e encerra sessões. */
    @Transactional
    public TemporaryCredential resetPassword(UUID facultyId, UUID id, AuthPrincipal actor, ClientInfo client) {
        User u = find(facultyId, id);
        if (u.getId().equals(actor.userId())) throw ApiException.forbidden("Use 'Alterar senha' no seu perfil");
        guard.ensureCanGrant(actor, u.getRoles());
        String temp = Tokens.temporaryPassword();
        u.setPasswordHash(encoder.encode(temp));
        u.setMustChangePassword(true);
        u.setLockedUntil(null);
        u.setFailedLoginCount(0);
        sessions.revokeAll(u.getId(), null, "PASSWORD_RESET_BY_ADMIN");
        audit.record(actor, facultyId, "PASSWORD_RESET_BY_ADMIN", "USER", id, Map.of(), client);
        notifications.notify(u.getId(), facultyId, "SEGURANCA", "Senha redefinida",
                "Sua senha foi redefinida pela equipe autorizada. Altere-a no próximo acesso.");
        return new TemporaryCredential(u.getUsername(), temp);
    }

    /** Recuperação de conta auditável: remove o autenticador; o usuário reconfigura no próximo login. */
    @Transactional
    public UserView resetMfa(UUID facultyId, UUID id, MfaReset req, AuthPrincipal actor, ClientInfo client) {
        User u = find(facultyId, id);
        if (u.getId().equals(actor.userId())) throw ApiException.forbidden("Use 'Gerenciar autenticador' no seu perfil");
        guard.ensureCanGrant(actor, u.getRoles());
        mfa.revokeAll(u);
        recoveryCodes.deleteAll(u);
        sessions.revokeAll(u.getId(), null, "MFA_RESET_BY_ADMIN");
        audit.record(actor, facultyId, "MFA_RESET_BY_ADMIN", "USER", id,
                Map.of("reason", req.reason(), "identityVerification", req.identityVerification()), client);
        notifications.notify(u.getId(), facultyId, "SEGURANCA", "Autenticador redefinido",
                "Seu autenticador foi redefinido pela equipe autorizada. Configure um novo no próximo acesso.");
        return UserView.of(u, clock.instant());
    }

    @Transactional(readOnly = true)
    public List<MeDtos.SessionView> sessions(UUID facultyId, UUID id) {
        User u = find(facultyId, id);
        return sessionRepo.findActiveByUser(u.getId(), clock.instant()).stream()
                .map(s -> new MeDtos.SessionView(s.getId(), s.getDeviceLabel(), s.getIpAddress(), s.getUserAgent(),
                        s.getCreatedAt(), s.getLastSeenAt(), s.getExpiresAt(), false)).toList();
    }

    @Transactional
    public int revokeSessions(UUID facultyId, UUID id, AuthPrincipal actor, ClientInfo client) {
        User u = find(facultyId, id);
        int n = sessions.revokeAll(u.getId(), null, "REVOKED_BY_ADMIN");
        audit.record(actor, facultyId, "USER_SESSIONS_REVOKED", "USER", id, Map.of("count", n), client);
        return n;
    }

    @Transactional(readOnly = true)
    public PageResponse<MeDtos.AccessView> accessHistory(UUID facultyId, UUID id, int page, int size) {
        User u = find(facultyId, id);
        return PageResponse.of(accessLogs.findByUserIdOrderByCreatedAtDesc(u.getId(), PageRequest.of(page, Math.min(size, 100))),
                a -> new MeDtos.AccessView(a.getEvent(), a.isSuccess(), a.getIpAddress(), a.getUserAgent(), a.getDetail(), a.getCreatedAt()));
    }

    private Set<Role> loadRoles(UUID facultyId, Set<UUID> ids) {
        Set<Role> rs = new HashSet<>();
        for (UUID rid : ids) {
            rs.add(roles.findByIdAndFacultyId(rid, facultyId).orElseThrow(() -> ApiException.badRequest("INVALID_ROLE", "Cargo inválido para esta instituição")));
        }
        return rs;
    }

    private User find(UUID facultyId, UUID id) {
        return users.findByIdAndFacultyId(id, facultyId).orElseThrow(() -> ApiException.notFound("Usuário"));
    }
}
