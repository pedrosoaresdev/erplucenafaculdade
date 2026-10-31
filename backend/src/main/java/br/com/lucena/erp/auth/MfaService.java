package br.com.lucena.erp.auth;

import br.com.lucena.erp.crypto.SecretCipher;
import br.com.lucena.erp.iam.User;
import br.com.lucena.erp.security.TotpService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.OptionalLong;

/** Ciclo de vida do segundo fator TOTP de um usuário. */
@Service
@RequiredArgsConstructor
public class MfaService {
    private final AuthenticatorSecretRepository repo;
    private final SecretCipher cipher;
    private final TotpService totp;
    private final Clock clock;

    public record EnrollmentData(String secret, String otpauthUri, String qrCodeDataUrl) {
    }

    /** Cria (ou substitui) o segredo PENDENTE. O segredo ativo, se houver, permanece válido até a confirmação. */
    @Transactional(propagation = Propagation.MANDATORY)
    public EnrollmentData startEnrollment(User user, String accountLabel) {
        repo.findByUserIdAndStatus(user.getId(), AuthenticatorSecret.Status.PENDING).ifPresent(p -> {
            repo.delete(p);
            repo.flush();
        });
        String secret = totp.generateSecret();
        AuthenticatorSecret s = new AuthenticatorSecret();
        s.setUserId(user.getId());
        s.setStatus(AuthenticatorSecret.Status.PENDING);
        s.setSecretCiphertext(cipher.encrypt(secret));
        repo.save(s);
        String uri = totp.otpauthUri(accountLabel, secret);
        return new EnrollmentData(TotpService.formatForDisplay(secret), uri, totp.qrCodeDataUrl(uri));
    }

    public boolean hasPending(User user) {
        return repo.findByUserIdAndStatus(user.getId(), AuthenticatorSecret.Status.PENDING).isPresent();
    }

    /** Valida o código contra o segredo ATIVO, rejeitando reutilização. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean verifyActive(User user, String code) {
        return repo.lockByUserIdAndStatus(user.getId(), AuthenticatorSecret.Status.ACTIVE)
                .map(s -> check(s, code))
                .orElse(false);
    }

    /** Valida o código contra o segredo PENDENTE e, se correto, ativa-o revogando o anterior. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean activatePending(User user, String code) {
        var pending = repo.lockByUserIdAndStatus(user.getId(), AuthenticatorSecret.Status.PENDING);
        if (pending.isEmpty() || !check(pending.get(), code)) {
            return false;
        }
        Instant now = clock.instant();
        repo.findByUserIdAndStatus(user.getId(), AuthenticatorSecret.Status.ACTIVE).ifPresent(old -> {
            old.setStatus(AuthenticatorSecret.Status.REVOKED);
            old.setRevokedAt(now);
            repo.saveAndFlush(old);
        });
        AuthenticatorSecret s = pending.get();
        s.setStatus(AuthenticatorSecret.Status.ACTIVE);
        s.setActivatedAt(now);
        user.setMfaEnabled(true);
        return true;
    }

    /** Revoga todos os segredos: o usuário deverá configurar novamente no próximo login. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void revokeAll(User user) {
        Instant now = clock.instant();
        repo.findByUserIdAndStatus(user.getId(), AuthenticatorSecret.Status.PENDING).ifPresent(repo::delete);
        repo.findByUserIdAndStatus(user.getId(), AuthenticatorSecret.Status.ACTIVE).ifPresent(s -> {
            s.setStatus(AuthenticatorSecret.Status.REVOKED);
            s.setRevokedAt(now);
        });
        user.setMfaEnabled(false);
    }

    public Instant activatedAt(User user) {
        return repo.findByUserIdAndStatus(user.getId(), AuthenticatorSecret.Status.ACTIVE)
                .map(AuthenticatorSecret::getActivatedAt).orElse(null);
    }

    private boolean check(AuthenticatorSecret s, String code) {
        String secret = cipher.decrypt(s.getSecretCiphertext());
        OptionalLong step = totp.verify(secret, code, s.getLastUsedStep());
        if (step.isEmpty()) return false;
        s.setLastUsedStep(step.getAsLong());
        return true;
    }
}
