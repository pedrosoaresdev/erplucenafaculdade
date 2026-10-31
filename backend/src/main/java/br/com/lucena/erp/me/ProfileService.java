package br.com.lucena.erp.me;

import br.com.lucena.erp.audit.AccessLogRepository;
import br.com.lucena.erp.audit.AuditService;
import br.com.lucena.erp.auth.*;
import br.com.lucena.erp.common.ApiException;
import br.com.lucena.erp.common.ClientInfo;
import br.com.lucena.erp.common.PageResponse;
import br.com.lucena.erp.config.ErpProperties;
import br.com.lucena.erp.iam.Role;
import br.com.lucena.erp.iam.User;
import br.com.lucena.erp.iam.UserRepository;
import br.com.lucena.erp.me.MeDtos.*;
import br.com.lucena.erp.notification.NotificationRepository;
import br.com.lucena.erp.notification.NotificationService;
import br.com.lucena.erp.platform.FacultyRepository;
import br.com.lucena.erp.security.AuthPrincipal;
import br.com.lucena.erp.security.PasswordPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/** Perfil e segurança da conta do próprio usuário. Operações sensíveis exigem step-up (senha atual + TOTP). */
@Service
@RequiredArgsConstructor
public class ProfileService {
    private final UserRepository users;
    private final FacultyRepository faculties;
    private final PasswordEncoder encoder;
    private final PasswordPolicy passwordPolicy;
    private final ChallengeService challenges;
    private final MfaService mfa;
    private final RecoveryCodeService recoveryCodes;
    private final SessionService sessionService;
    private final UserSessionRepository sessionRepo;
    private final AccessLogRepository accessLogs;
    private final NotificationRepository notificationRepo;
    private final NotificationService notifications;
    private final AuditService audit;
    private final ErpProperties props;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Profile profile(AuthPrincipal p) {
        User u = load(p);
        FacultyInfo fi = u.getFacultyId() == null ? null : faculties.findById(u.getFacultyId())
                .map(f -> new FacultyInfo(f.getId(), f.getSlug(), f.getName(), f.getLogoUrl())).orElse(null);
        String registration = jdbc.query("select registration from students where user_id = ?",
                rs -> rs.next() ? rs.getString(1) : null, u.getId());
        return new Profile(u.getId(), u.getUsername(), u.getFullName(), u.getSocialName(), u.getEmail(), u.getPhone(),
                u.getPhotoUrl(), u.getStatus().name(), u.getCreatedAt(), u.getLastLoginAt(), u.isPlatformAdmin(),
                u.isMustChangePassword(), u.isMfaEnabled(), fi,
                u.getRoles().stream().map(Role::getName).sorted().toList(), p.permissions(), registration);
    }

    @Transactional
    public Profile update(AuthPrincipal p, UpdateProfile req, ClientInfo client) {
        User u = load(p);
        u.setSocialName(blankToNull(req.socialName()));
        u.setPhone(blankToNull(req.phone()));
        u.setPhotoUrl(blankToNull(req.photoUrl()));
        audit.record(p, u.getFacultyId(), "PROFILE_UPDATED", "USER", u.getId(), Map.of("fields", List.of("socialName", "phone", "photoUrl")), client);
        return profile(p);
    }

    @Transactional
    public Profile updateEmail(AuthPrincipal p, UpdateEmail req, ClientInfo client) {
        User u = load(p);
        consumeStepUp(u, req.stepUpToken(), AuthChallenge.Purpose.STEPUP_PROFILE_SENSITIVE);
        u.setEmail(req.email().trim().toLowerCase());
        audit.record(p, u.getFacultyId(), "EMAIL_CHANGED", "USER", u.getId(), Map.of(), client);
        notifications.notify(u.getId(), u.getFacultyId(), "SEGURANCA", "E-mail alterado", "O e-mail da sua conta foi alterado.");
        return profile(p);
    }

    /** Reautenticação: senha atual + código TOTP -> token de uso único, curto e vinculado ao propósito. */
    @Transactional(noRollbackFor = ApiException.class)
    public StepUpResponse stepUp(AuthPrincipal p, StepUpRequest req, ClientInfo client) {
        User u = load(p);
        Instant now = clock.instant();
        boolean passwordOk = encoder.matches(req.currentPassword(), u.getPasswordHash());
        // O código só é avaliado (e marcado como usado) se a senha estiver correta.
        boolean codeOk = passwordOk && mfa.verifyActive(u, req.totpCode());
        if (!passwordOk || !codeOk) {
            int count = u.getFailedLoginCount() + 1;
            if (count >= props.security().maxFailedLogins()) {
                u.setLockedUntil(now.plus(props.security().lockoutDuration()));
                u.setFailedLoginCount(0);
                sessionService.revokeAll(u.getId(), null, "LOCKED");
            } else {
                u.setFailedLoginCount(count);
            }
            audit.access(u.getId(), u.getFacultyId(), u.getUsername(), "STEP_UP_" + req.purpose(), false,
                    passwordOk ? "código inválido" : "senha atual incorreta", client);
            throw new ApiException(HttpStatus.BAD_REQUEST, "STEP_UP_FAILED", "Senha atual ou código do autenticador inválidos");
        }
        u.setFailedLoginCount(0);
        Duration ttl = props.security().stepUpTtl();
        String token = challenges.create(u.getId(), req.purpose().challengePurpose, ttl, client);
        audit.access(u.getId(), u.getFacultyId(), u.getUsername(), "STEP_UP_" + req.purpose(), true, null, client);
        return new StepUpResponse(token, ttl.toSeconds());
    }

    @Transactional
    public void changePassword(AuthPrincipal p, ChangePassword req, ClientInfo client) {
        User u = load(p);
        consumeStepUp(u, req.stepUpToken(), AuthChallenge.Purpose.STEPUP_PASSWORD_CHANGE);
        passwordPolicy.validate(req.newPassword(), req.confirmPassword(), u.getUsername());
        if (encoder.matches(req.newPassword(), u.getPasswordHash())) {
            throw ApiException.badRequest("PASSWORD_SAME_AS_CURRENT", "A nova senha deve ser diferente da atual");
        }
        u.setPasswordHash(encoder.encode(req.newPassword()));
        u.setPasswordChangedAt(clock.instant());
        u.setMustChangePassword(false);
        // Todas as sessões (inclusive a atual) são encerradas: o usuário deve entrar novamente.
        int revoked = sessionService.revokeAll(u.getId(), null, "PASSWORD_CHANGED");
        audit.record(p, u.getFacultyId(), "PASSWORD_CHANGED", "USER", u.getId(), Map.of("sessionsRevoked", revoked), client);
        notifications.notify(u.getId(), u.getFacultyId(), "SEGURANCA", "Senha alterada",
                "A senha da sua conta foi alterada. Se não foi você, contate o suporte imediatamente.");
    }

    @Transactional(readOnly = true)
    public MfaStatus mfaStatus(AuthPrincipal p) {
        User u = load(p);
        return new MfaStatus(u.isMfaEnabled(), mfa.activatedAt(u), recoveryCodes.remaining(u), mfa.hasPending(u));
    }

    /** Troca de dispositivo, passo 1: exige step-up; gera novo segredo pendente (o atual continua válido). */
    @Transactional
    public MfaEnrollment startRotation(AuthPrincipal p, StepUpOnly req, ClientInfo client) {
        User u = load(p);
        consumeStepUp(u, req.stepUpToken(), AuthChallenge.Purpose.STEPUP_MFA_ROTATE);
        String label = u.getFacultyId() == null ? u.getUsername() + " (Master)"
                : u.getUsername() + " - " + faculties.findById(u.getFacultyId()).map(f -> f.getSlug()).orElse("");
        MfaService.EnrollmentData d = mfa.startEnrollment(u, label);
        audit.record(p, u.getFacultyId(), "MFA_ROTATION_STARTED", "USER", u.getId(), Map.of(), client);
        return new MfaEnrollment(d.secret(), d.otpauthUri(), d.qrCodeDataUrl(), label);
    }

    /** Troca de dispositivo, passo 2: valida o código do NOVO dispositivo, revoga o segredo anterior. */
    @Transactional(noRollbackFor = ApiException.class)
    public RecoveryCodes confirmRotation(AuthPrincipal p, CodeOnly req, ClientInfo client) {
        User u = load(p);
        if (!mfa.hasPending(u)) {
            throw ApiException.badRequest("MFA_ROTATION_NOT_STARTED", "Inicie a troca de dispositivo antes de validar");
        }
        if (!mfa.activatePending(u, req.code())) {
            audit.access(u.getId(), u.getFacultyId(), u.getUsername(), "MFA_ROTATION_CONFIRM", false, "código inválido", client);
            throw ApiException.badRequest("INVALID_CODE", "Código inválido. Verifique o novo dispositivo");
        }
        List<String> codes = recoveryCodes.regenerate(u);
        sessionService.revokeAll(u.getId(), p.sessionId(), "MFA_ROTATED");
        audit.record(p, u.getFacultyId(), "MFA_ROTATED", "USER", u.getId(), Map.of("otherSessionsRevoked", true), client);
        notifications.notify(u.getId(), u.getFacultyId(), "SEGURANCA", "Autenticador substituído",
                "Seu autenticador foi trocado e novos códigos de recuperação foram gerados.");
        return new RecoveryCodes(codes);
    }

    /** Desativação: como o 2º fator é obrigatório, a conta volta ao estado de configuração no próximo login. */
    @Transactional
    public void disableMfa(AuthPrincipal p, StepUpOnly req, ClientInfo client) {
        User u = load(p);
        consumeStepUp(u, req.stepUpToken(), AuthChallenge.Purpose.STEPUP_MFA_DISABLE);
        mfa.revokeAll(u);
        recoveryCodes.deleteAll(u);
        sessionService.revokeAll(u.getId(), null, "MFA_DISABLED");
        audit.record(p, u.getFacultyId(), "MFA_DISABLED", "USER", u.getId(), Map.of("reconfigurationRequired", true), client);
        notifications.notify(u.getId(), u.getFacultyId(), "SEGURANCA", "Autenticador desativado",
                "Você precisará configurar um novo autenticador no próximo acesso.");
    }

    @Transactional
    public RecoveryCodes regenerateRecoveryCodes(AuthPrincipal p, StepUpOnly req, ClientInfo client) {
        User u = load(p);
        consumeStepUp(u, req.stepUpToken(), AuthChallenge.Purpose.STEPUP_RECOVERY_CODES);
        List<String> codes = recoveryCodes.regenerate(u);
        audit.record(p, u.getFacultyId(), "RECOVERY_CODES_REGENERATED", "USER", u.getId(), Map.of(), client);
        return new RecoveryCodes(codes);
    }

    @Transactional(readOnly = true)
    public List<SessionView> sessions(AuthPrincipal p) {
        return sessionRepo.findActiveByUser(p.userId(), clock.instant()).stream()
                .map(s -> new SessionView(s.getId(), s.getDeviceLabel(), s.getIpAddress(), s.getUserAgent(), s.getCreatedAt(),
                        s.getLastSeenAt(), s.getExpiresAt(), s.getId().equals(p.sessionId())))
                .toList();
    }

    @Transactional
    public void revokeSession(AuthPrincipal p, UUID sessionId, ClientInfo client) {
        UserSession s = sessionRepo.findById(sessionId).filter(x -> x.getUserId().equals(p.userId()))
                .orElseThrow(() -> ApiException.notFound("Sessão"));
        sessionService.revoke(s, "USER_REVOKED");
        audit.record(p, p.facultyId(), "SESSION_REVOKED", "USER_SESSION", sessionId, Map.of(), client);
    }

    @Transactional
    public int revokeOtherSessions(AuthPrincipal p, ClientInfo client) {
        int n = sessionService.revokeAll(p.userId(), p.sessionId(), "USER_REVOKED_OTHERS");
        audit.record(p, p.facultyId(), "OTHER_SESSIONS_REVOKED", "USER", p.userId(), Map.of("count", n), client);
        return n;
    }

    @Transactional(readOnly = true)
    public PageResponse<AccessView> accessHistory(AuthPrincipal p, int page, int size) {
        return PageResponse.of(accessLogs.findByUserIdOrderByCreatedAtDesc(p.userId(), PageRequest.of(page, Math.min(size, 100))),
                a -> new AccessView(a.getEvent(), a.isSuccess(), a.getIpAddress(), a.getUserAgent(), a.getDetail(), a.getCreatedAt()));
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationView> notifications(AuthPrincipal p, int page, int size) {
        return PageResponse.of(notificationRepo.findByUserIdOrderByCreatedAtDesc(p.userId(), PageRequest.of(page, Math.min(size, 100))),
                n -> new NotificationView(n.getId(), n.getCategory(), n.getTitle(), n.getBody(), n.getLink(), n.getReadAt(), n.getCreatedAt()));
    }

    public long unreadNotifications(AuthPrincipal p) {
        return notificationRepo.countByUserIdAndReadAtIsNull(p.userId());
    }

    @Transactional
    public void markNotificationRead(AuthPrincipal p, UUID id) {
        notificationRepo.findByIdAndUserId(id, p.userId()).orElseThrow(() -> ApiException.notFound("Notificação"))
                .setReadAt(clock.instant());
    }

    @Transactional
    public void markAllNotificationsRead(AuthPrincipal p) {
        notificationRepo.markAllRead(p.userId(), clock.instant());
    }

    private void consumeStepUp(User u, String token, AuthChallenge.Purpose purpose) {
        AuthChallenge ch;
        try {
            ch = challenges.lockUsable(token, Set.of(purpose));
        } catch (ApiException e) {
            throw new ApiException(HttpStatus.FORBIDDEN, "STEP_UP_REQUIRED", "Confirme sua identidade novamente (senha e código)");
        }
        if (!ch.getUserId().equals(u.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "STEP_UP_REQUIRED", "Confirme sua identidade novamente (senha e código)");
        }
        challenges.consume(ch);
    }

    private User load(AuthPrincipal p) {
        return users.findById(p.userId()).orElseThrow(() -> ApiException.notFound("Usuário"));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
