package br.com.lucena.erp.auth;

import br.com.lucena.erp.common.ApiException;
import br.com.lucena.erp.common.ClientInfo;
import br.com.lucena.erp.crypto.Tokens;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;

/** Desafios opacos de uso único (somente o hash SHA-256 é persistido). */
@Service
@RequiredArgsConstructor
public class ChallengeService {
    private final AuthChallengeRepository repo;
    private final Clock clock;

    @Transactional
    public String create(UUID userId, AuthChallenge.Purpose purpose, Duration ttl, ClientInfo client) {
        String raw = Tokens.randomUrlToken(32);
        AuthChallenge c = new AuthChallenge();
        c.setUserId(userId);
        c.setPurpose(purpose);
        c.setTokenHash(Tokens.sha256Hex(raw));
        c.setExpiresAt(clock.instant().plus(ttl));
        c.setIpAddress(client == null ? null : client.ip());
        repo.save(c);
        return raw;
    }

    /** Carrega com bloqueio de linha um desafio válido e do propósito esperado. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY, noRollbackFor = ApiException.class)
    public AuthChallenge lockUsable(String raw, Set<AuthChallenge.Purpose> purposes) {
        if (raw == null || raw.isBlank()) {
            throw ApiException.unauthorized("CHALLENGE_INVALID", "Sessão de login expirada. Entre novamente");
        }
        AuthChallenge c = repo.lockByTokenHash(Tokens.sha256Hex(raw))
                .filter(ch -> purposes.contains(ch.getPurpose()) && ch.isUsable(clock.instant()))
                .orElseThrow(() -> ApiException.unauthorized("CHALLENGE_INVALID", "Sessão de login expirada. Entre novamente"));
        return c;
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void consume(AuthChallenge c) {
        c.setConsumedAt(clock.instant());
    }

    @Scheduled(fixedDelay = 3_600_000)
    @Transactional
    public void purgeExpired() {
        repo.deleteExpired(clock.instant().minus(Duration.ofDays(1)));
    }
}
