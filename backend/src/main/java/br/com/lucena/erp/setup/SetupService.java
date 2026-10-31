package br.com.lucena.erp.setup;

import br.com.lucena.erp.audit.AuditService;
import br.com.lucena.erp.auth.*;
import br.com.lucena.erp.common.ApiException;
import br.com.lucena.erp.common.ClientInfo;
import br.com.lucena.erp.config.ErpProperties;
import br.com.lucena.erp.crypto.SecretCipher;
import br.com.lucena.erp.crypto.Tokens;
import br.com.lucena.erp.iam.User;
import br.com.lucena.erp.iam.UserRepository;
import br.com.lucena.erp.security.PasswordPolicy;
import br.com.lucena.erp.security.TotpService;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

/**
 * Instalação inicial: cria o primeiro administrador Master. Só funciona enquanto não existir nenhum Master e
 * exige (1) o e-mail do dono configurado em ERP_OWNER_EMAIL e (2) o código de instalação de uso único exibido
 * apenas no console do servidor. Depois: Google Authenticator validado e criação de usuário e senha.
 */
@Service
@RequiredArgsConstructor
public class SetupService {
    static final int MAX_ATTEMPTS = 5;
    static final Duration CODE_TTL = Duration.ofHours(24);
    static final Duration TOKEN_TTL = Duration.ofMinutes(20);
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private final UserRepository users;
    private final ErpProperties props;
    private final JdbcTemplate jdbc;
    private final SecretCipher cipher;
    private final TotpService totp;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder encoder;
    private final AuthenticatorSecretRepository secrets;
    private final RecoveryCodeService recoveryCodes;
    private final SessionService sessions;
    private final AuditService audit;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public record Status(boolean setupRequired, boolean ownerConfigured) {
    }

    public record StartRequest(@NotBlank @Email @Size(max = 200) String email, @NotBlank @Size(max = 40) String setupCode) {
    }

    public record TokenRequest(@NotBlank String setupToken) {
    }

    public record CodeRequest(@NotBlank String setupToken, @NotBlank @Size(max = 12) String code) {
    }

    public record CompleteRequest(@NotBlank String setupToken,
                                  @NotBlank @Pattern(regexp = "^[A-Za-z0-9._-]{3,60}$", message = "3 a 60 caracteres: letras, números, ponto, hífen ou sublinhado") String username,
                                  @NotBlank @Size(max = 200) String fullName,
                                  @NotBlank @Size(max = 128) String password,
                                  @NotBlank @Size(max = 128) String confirmPassword) {
    }

    public record Enrollment(String setupToken, long expiresIn, String secret, String otpauthUri, String qrCodeDataUrl, String accountLabel) {
    }

    public record Completed(java.util.UUID userId, AuthDtos.TokenResponse body, SessionService.IssuedTokens tokens) {
    }

    public Status status() {
        boolean owner = ownerEmail() != null;
        return new Status(owner && !users.existsByPlatformAdminTrue(), owner);
    }

    /** Gera (ou substitui) o código de instalação e retorna o valor em claro para exibição no console. */
    @Transactional
    public String issueCode() {
        if (!status().setupRequired()) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            if (i > 0 && i % 4 == 0) sb.append('-');
            sb.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        String code = sb.toString();
        jdbc.update("""
                insert into platform_setup (id, code_hash, code_expires_at, attempts) values (1, ?, ?, 0)
                on conflict (id) do update set code_hash = excluded.code_hash, code_expires_at = excluded.code_expires_at, attempts = 0,
                  token_hash = null, token_expires_at = null, secret_ciphertext = null, totp_verified = false, last_used_step = null,
                  completed_at = null, updated_at = now()""", Tokens.sha256Hex(code), Timestamp.from(clock.instant().plus(CODE_TTL)));
        return code;
    }

    /** Etapa 1: e-mail do dono + código de instalação → token da instalação e QR Code do autenticador. */
    @Transactional(noRollbackFor = ApiException.class)
    public Enrollment start(StartRequest req, ClientInfo client) {
        ensureRequired();
        Row row = lockRow();
        Instant now = clock.instant();
        if (row.attempts() >= MAX_ATTEMPTS) {
            throw new ApiException(HttpStatus.LOCKED, "SETUP_LOCKED", "Tentativas esgotadas. Reinicie o servidor para gerar um novo código de instalação");
        }
        if (row.codeExpiresAt().isBefore(now)) {
            throw new ApiException(HttpStatus.GONE, "SETUP_CODE_EXPIRED", "Código de instalação expirado. Reinicie o servidor para gerar outro");
        }
        boolean emailOk = req.email().trim().equalsIgnoreCase(ownerEmail());
        boolean codeOk = MessageDigest.isEqual(Tokens.sha256Hex(normalize(req.setupCode())).getBytes(StandardCharsets.US_ASCII),
                row.codeHash().getBytes(StandardCharsets.US_ASCII));
        if (!emailOk || !codeOk) {
            jdbc.update("update platform_setup set attempts = attempts + 1, updated_at = now() where id = 1");
            audit.access(null, null, req.email(), "PLATFORM_SETUP", false, "e-mail ou código inválido", client);
            throw ApiException.unauthorized("SETUP_INVALID", "E-mail ou código de instalação inválidos. Tentativas restantes: "
                    + (MAX_ATTEMPTS - row.attempts() - 1));
        }
        String token = Tokens.randomUrlToken(32);
        String secret = totp.generateSecret();
        jdbc.update("""
                update platform_setup set token_hash = ?, token_expires_at = ?, secret_ciphertext = ?, totp_verified = false,
                  last_used_step = null, updated_at = now() where id = 1""",
                Tokens.sha256Hex(token), Timestamp.from(now.plus(TOKEN_TTL)), cipher.encrypt(secret));
        audit.access(null, null, req.email(), "PLATFORM_SETUP", true, "código de instalação aceito", client);
        return enrollment(token, secret);
    }

    /** Gera novo segredo enquanto o autenticador ainda não foi validado. */
    @Transactional
    public Enrollment regenerate(TokenRequest req) {
        Row row = checkToken(req.setupToken());
        if (row.totpVerified()) throw ApiException.conflict("TOTP_ALREADY_VERIFIED", "O autenticador já foi validado");
        String secret = totp.generateSecret();
        jdbc.update("update platform_setup set secret_ciphertext = ?, last_used_step = null, updated_at = now() where id = 1", cipher.encrypt(secret));
        return enrollment(req.setupToken(), secret);
    }

    /** Etapa 2: valida o código do Google Authenticator. */
    @Transactional
    public void verifyTotp(CodeRequest req) {
        Row row = checkToken(req.setupToken());
        String secret = cipher.decrypt(row.secretCiphertext());
        OptionalLong step = totp.verify(secret, req.code(), row.lastUsedStep());
        if (step.isEmpty()) throw ApiException.badRequest("INVALID_CODE", "Código inválido. Confira o Google Authenticator e tente novamente");
        jdbc.update("update platform_setup set totp_verified = true, last_used_step = ?, updated_at = now() where id = 1", step.getAsLong());
    }

    /** Etapa 3: cria usuário e senha do administrador Master e conclui a instalação (já autenticado). */
    @Transactional
    public Completed complete(CompleteRequest req, ClientInfo client) {
        Row row = checkToken(req.setupToken());
        if (!row.totpVerified()) throw ApiException.badRequest("TOTP_NOT_VERIFIED", "Valide o Google Authenticator antes de criar o usuário");
        passwordPolicy.validate(req.password(), req.confirmPassword(), req.username());
        if (users.findPlatformUser(req.username()).isPresent()) throw ApiException.conflict("USERNAME_IN_USE", "Nome de usuário já utilizado");
        User u = new User();
        u.setUsername(req.username().trim());
        u.setFullName(req.fullName().trim());
        u.setEmail(ownerEmail());
        u.setPasswordHash(encoder.encode(req.password()));
        u.setPlatformAdmin(true);
        u.setMfaEnabled(true);
        u.setPasswordChangedAt(clock.instant());
        u.setLastLoginAt(clock.instant());
        users.saveAndFlush(u);
        AuthenticatorSecret s = new AuthenticatorSecret();
        s.setUserId(u.getId());
        s.setSecretCiphertext(row.secretCiphertext());
        s.setStatus(AuthenticatorSecret.Status.ACTIVE);
        s.setActivatedAt(clock.instant());
        s.setLastUsedStep(row.lastUsedStep());
        secrets.save(s);
        List<String> codes = recoveryCodes.regenerate(u);
        jdbc.update("""
                update platform_setup set completed_at = now(), token_hash = null, secret_ciphertext = null, updated_at = now() where id = 1""");
        audit.recordSelf(u.getId(), u.getUsername(), null, "PLATFORM_SETUP_COMPLETED", Map.of("email", u.getEmail()), client);
        SessionService.IssuedTokens t = sessions.create(u, client);
        return new Completed(u.getId(), new AuthDtos.TokenResponse(t.accessToken(), "Bearer", t.expiresIn(), false, codes, (long) codes.size()), t);
    }

    // ---------------------------------------------------------------

    record Row(String codeHash, Instant codeExpiresAt, int attempts, String tokenHash, Instant tokenExpiresAt, String secretCiphertext,
               boolean totpVerified, Long lastUsedStep) {
    }

    private Row lockRow() {
        Row row = jdbc.query("""
                select code_hash, code_expires_at, attempts, token_hash, token_expires_at, secret_ciphertext, totp_verified, last_used_step
                from platform_setup where id = 1 and completed_at is null for update""",
                rs -> rs.next() ? new Row(rs.getString(1), rs.getTimestamp(2).toInstant(), rs.getInt(3), rs.getString(4),
                        rs.getTimestamp(5) == null ? null : rs.getTimestamp(5).toInstant(), rs.getString(6), rs.getBoolean(7),
                        (Long) rs.getObject(8)) : null);
        if (row == null) throw ApiException.conflict("SETUP_NOT_INITIALIZED", "Código de instalação não gerado. Reinicie o servidor");
        return row;
    }

    private Row checkToken(String token) {
        ensureRequired();
        Row row = lockRow();
        if (row.tokenHash() == null || row.tokenExpiresAt() == null || row.tokenExpiresAt().isBefore(clock.instant())
                || !MessageDigest.isEqual(Tokens.sha256Hex(token).getBytes(StandardCharsets.US_ASCII), row.tokenHash().getBytes(StandardCharsets.US_ASCII))) {
            throw ApiException.unauthorized("SETUP_SESSION_EXPIRED", "Sessão de instalação expirada. Recomece informando o e-mail e o código");
        }
        return row;
    }

    private void ensureRequired() {
        if (!status().setupRequired()) throw ApiException.notFound("Instalação pendente");
    }

    private Enrollment enrollment(String token, String secret) {
        String label = ownerEmail() + " (Master)";
        String uri = totp.otpauthUri(label, secret);
        return new Enrollment(token, TOKEN_TTL.toSeconds(), TotpService.formatForDisplay(secret), uri, totp.qrCodeDataUrl(uri), label);
    }

    private String ownerEmail() {
        String e = props.setup() == null ? null : props.setup().ownerEmail();
        return e == null || e.isBlank() ? null : e.trim().toLowerCase();
    }

    private static String normalize(String code) {
        String c = code == null ? "" : code.toUpperCase().replaceAll("[^A-Z0-9]", "");
        return c.length() == 12 ? c.substring(0, 4) + "-" + c.substring(4, 8) + "-" + c.substring(8) : c;
    }
}
