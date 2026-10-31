package br.com.lucena.erp.auth;

import br.com.lucena.erp.audit.AuditService;
import br.com.lucena.erp.auth.AuthDtos.*;
import br.com.lucena.erp.common.ApiException;
import br.com.lucena.erp.common.ClientInfo;
import br.com.lucena.erp.config.ErpProperties;
import br.com.lucena.erp.iam.User;
import br.com.lucena.erp.iam.UserRepository;
import br.com.lucena.erp.notification.NotificationService;
import br.com.lucena.erp.platform.Faculty;
import br.com.lucena.erp.platform.FacultyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fluxo de login em etapas:
 * 1) usuário/matrícula + senha  -> desafio (MFA_SETUP_REQUIRED ou MFA_REQUIRED)
 * 2a) primeiro acesso: setup (QR + chave) -> activate (código validado) -> sessão + códigos de recuperação
 * 2b) acessos seguintes: verify (código TOTP) ou recovery (código de recuperação) -> sessão
 * Mensagens de erro de credencial são genéricas para não revelar a existência de contas.
 */
@Service
@RequiredArgsConstructor
public class AuthService {
    private static final Set<AuthChallenge.Purpose> LOGIN = Set.of(AuthChallenge.Purpose.LOGIN_MFA);
    private static final Set<AuthChallenge.Purpose> SETUP = Set.of(AuthChallenge.Purpose.LOGIN_MFA_SETUP);

    private final UserRepository users;
    private final FacultyRepository faculties;
    private final PasswordEncoder encoder;
    private final ChallengeService challenges;
    private final MfaService mfa;
    private final RecoveryCodeService recoveryCodes;
    private final SessionService sessions;
    private final AuditService audit;
    private final NotificationService notifications;
    private final ErpProperties props;
    private final Clock clock;

    /** Hash Argon2 de referência para equalizar o tempo de resposta quando o usuário não existe. */
    private volatile String dummyHash;

    @Transactional(noRollbackFor = ApiException.class)
    public LoginResponse login(LoginRequest req, ClientInfo client) {
        Instant now = clock.instant();
        Faculty faculty = null;
        if (req.faculty() != null && !req.faculty().isBlank()) {
            faculty = faculties.findBySlug(req.faculty().trim().toLowerCase()).orElse(null);
        }
        String identifier = req.identifier().trim();
        User user = null;
        if (faculty != null) {
            user = users.findInFaculty(faculty.getId(), identifier).orElse(null);
        } else if (req.faculty() == null || req.faculty().isBlank()) {
            user = users.findPlatformUser(identifier).orElse(null);
        }

        if (user == null) {
            encoder.matches(req.password(), dummyHash());
            audit.access(null, faculty == null ? null : faculty.getId(), identifier, "LOGIN_PASSWORD", false, "usuário inexistente", client);
            throw invalidCredentials();
        }
        if (user.isLocked(now)) {
            audit.access(user.getId(), user.getFacultyId(), identifier, "LOGIN_PASSWORD", false, "conta bloqueada temporariamente", client);
            throw locked();
        }
        if (!encoder.matches(req.password(), user.getPasswordHash())) {
            registerFailure(user, now);
            audit.access(user.getId(), user.getFacultyId(), identifier, "LOGIN_PASSWORD", false, "senha incorreta", client);
            throw user.isLocked(now) ? locked() : invalidCredentials();
        }
        ensureCanLogin(user);
        user.setFailedLoginCount(0);

        AuthChallenge.Purpose purpose = user.isMfaEnabled() ? AuthChallenge.Purpose.LOGIN_MFA : AuthChallenge.Purpose.LOGIN_MFA_SETUP;
        String token = challenges.create(user.getId(), purpose, props.security().challengeTtl(), client);
        audit.access(user.getId(), user.getFacultyId(), identifier, "LOGIN_PASSWORD", true, null, client);
        return new LoginResponse(user.isMfaEnabled() ? NextStep.MFA_REQUIRED : NextStep.MFA_SETUP_REQUIRED,
                token, props.security().challengeTtl().toSeconds(), user.displayName());
    }

    /** Primeiro acesso: gera QR Code e chave. Pode ser chamado novamente para gerar novo segredo enquanto não ativado. */
    @Transactional
    public MfaSetupResponse setupMfa(String challengeToken) {
        AuthChallenge ch = challenges.lockUsable(challengeToken, SETUP);
        User user = users.findById(ch.getUserId()).orElseThrow();
        if (user.isMfaEnabled()) {
            throw ApiException.conflict("MFA_ALREADY_ACTIVE", "O autenticador já está configurado");
        }
        String label = accountLabel(user);
        MfaService.EnrollmentData data = mfa.startEnrollment(user, label);
        return new MfaSetupResponse(data.secret(), data.otpauthUri(), data.qrCodeDataUrl(), label);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public Issued activateMfa(CodeRequest req, ClientInfo client) {
        AuthChallenge ch = challenges.lockUsable(req.challengeToken(), SETUP);
        User user = users.findById(ch.getUserId()).orElseThrow();
        ensureCanLogin(user);
        if (!mfa.hasPending(user)) {
            throw ApiException.badRequest("MFA_SETUP_NOT_STARTED", "Gere o QR Code antes de validar o código");
        }
        if (!mfa.activatePending(user, req.code())) {
            failChallenge(ch, user, client, "MFA_ACTIVATE");
        }
        challenges.consume(ch);
        List<String> codes = recoveryCodes.regenerate(user);
        user.setLastLoginAt(clock.instant());
        audit.recordSelf(user.getId(), user.getUsername(), user.getFacultyId(), "MFA_ACTIVATED", Map.of("method", "TOTP"), client);
        audit.access(user.getId(), user.getFacultyId(), user.getUsername(), "LOGIN_MFA_SETUP", true, null, client);
        notifications.notify(user.getId(), user.getFacultyId(), "SEGURANCA", "Autenticador ativado",
                "A verificação em duas etapas foi ativada na sua conta.");
        SessionService.IssuedTokens t = sessions.create(user, client);
        return new Issued(t, new TokenResponse(t.accessToken(), "Bearer", t.expiresIn(), user.isMustChangePassword(), codes, (long) codes.size()));
    }

    @Transactional(noRollbackFor = ApiException.class)
    public Issued verifyMfa(CodeRequest req, ClientInfo client) {
        AuthChallenge ch = challenges.lockUsable(req.challengeToken(), LOGIN);
        User user = users.findById(ch.getUserId()).orElseThrow();
        ensureCanLogin(user);
        if (!mfa.verifyActive(user, req.code())) {
            failChallenge(ch, user, client, "LOGIN_MFA");
        }
        return completeLogin(ch, user, client, "LOGIN_MFA", null);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public Issued recoveryLogin(RecoveryLoginRequest req, ClientInfo client) {
        AuthChallenge ch = challenges.lockUsable(req.challengeToken(), LOGIN);
        User user = users.findById(ch.getUserId()).orElseThrow();
        ensureCanLogin(user);
        if (!recoveryCodes.consume(user, req.recoveryCode())) {
            failChallenge(ch, user, client, "LOGIN_RECOVERY_CODE");
        }
        audit.recordSelf(user.getId(), user.getUsername(), user.getFacultyId(), "RECOVERY_CODE_USED", Map.of(), client);
        notifications.notify(user.getId(), user.getFacultyId(), "SEGURANCA", "Código de recuperação utilizado",
                "Um código de recuperação foi usado para acessar sua conta. Se não foi você, contate o suporte.");
        return completeLogin(ch, user, client, "LOGIN_RECOVERY_CODE", recoveryCodes.remaining(user));
    }

    public record Issued(SessionService.IssuedTokens tokens, TokenResponse body) {
    }

    private Issued completeLogin(AuthChallenge ch, User user, ClientInfo client, String event, Long remaining) {
        challenges.consume(ch);
        user.setFailedLoginCount(0);
        user.setLastLoginAt(clock.instant());
        audit.access(user.getId(), user.getFacultyId(), user.getUsername(), event, true, null, client);
        SessionService.IssuedTokens t = sessions.create(user, client);
        return new Issued(t, new TokenResponse(t.accessToken(), "Bearer", t.expiresIn(), user.isMustChangePassword(), null,
                remaining != null ? remaining : recoveryCodes.remaining(user)));
    }

    /** Código incorreto: conta tentativa no desafio e no usuário (proteção contra força bruta entre desafios). */
    private void failChallenge(AuthChallenge ch, User user, ClientInfo client, String event) {
        Instant now = clock.instant();
        ch.setAttempts(ch.getAttempts() + 1);
        if (ch.getAttempts() >= props.security().maxChallengeAttempts()) {
            ch.setConsumedAt(now);
        }
        registerFailure(user, now);
        audit.access(user.getId(), user.getFacultyId(), user.getUsername(), event, false, "código inválido", client);
        if (user.isLocked(now)) throw locked();
        if (ch.getConsumedAt() != null) {
            throw ApiException.unauthorized("CHALLENGE_INVALID", "Tentativas esgotadas. Entre novamente");
        }
        int left = props.security().maxChallengeAttempts() - ch.getAttempts();
        throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CODE",
                "Código inválido ou já utilizado. Tentativas restantes: " + left);
    }

    private void registerFailure(User user, Instant now) {
        int count = user.getFailedLoginCount() + 1;
        if (count >= props.security().maxFailedLogins()) {
            user.setLockedUntil(now.plus(props.security().lockoutDuration()));
            user.setFailedLoginCount(0);
        } else {
            user.setFailedLoginCount(count);
        }
    }

    private void ensureCanLogin(User user) {
        if (user.getStatus() != User.Status.ACTIVE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "Conta desativada ou bloqueada. Procure a secretaria/suporte");
        }
        if (user.isLocked(clock.instant())) throw locked();
        if (user.getFacultyId() != null) {
            Faculty f = faculties.findById(user.getFacultyId()).orElseThrow();
            if (f.getStatus() != Faculty.Status.ACTIVE) {
                throw new ApiException(HttpStatus.FORBIDDEN, "FACULTY_SUSPENDED", "Acesso da instituição temporariamente indisponível");
            }
        }
    }

    private String accountLabel(User user) {
        if (user.getFacultyId() == null) return user.getUsername() + " (Master)";
        return faculties.findById(user.getFacultyId()).map(f -> user.getUsername() + " - " + f.getSlug()).orElse(user.getUsername());
    }

    private String dummyHash() {
        if (dummyHash == null) dummyHash = encoder.encode("dummy-password-for-timing");
        return dummyHash;
    }

    private static ApiException invalidCredentials() {
        return ApiException.unauthorized("INVALID_CREDENTIALS", "Usuário/matrícula ou senha inválidos");
    }

    private ApiException locked() {
        return new ApiException(HttpStatus.LOCKED, "ACCOUNT_LOCKED",
                "Muitas tentativas inválidas. Conta bloqueada temporariamente por "
                        + props.security().lockoutDuration().toMinutes() + " minutos");
    }
}
