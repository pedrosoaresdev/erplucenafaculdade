package br.com.lucena.erp.auth;

import br.com.lucena.erp.common.ApiException;
import br.com.lucena.erp.common.ClientInfo;
import br.com.lucena.erp.config.ErpProperties;
import br.com.lucena.erp.crypto.Tokens;
import br.com.lucena.erp.iam.User;
import br.com.lucena.erp.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** Sessões do servidor: refresh token opaco (hash no banco) + access token JWT curto vinculado à sessão. */
@Service
@RequiredArgsConstructor
public class SessionService {
    private final UserSessionRepository repo;
    private final JwtService jwt;
    private final ErpProperties props;
    private final Clock clock;

    public record IssuedTokens(String accessToken, long expiresIn, String refreshToken, UUID sessionId) {
    }

    @Transactional
    public IssuedTokens create(User user, ClientInfo client) {
        String refresh = Tokens.randomUrlToken(48);
        UserSession s = new UserSession();
        s.setUserId(user.getId());
        s.setFacultyId(user.getFacultyId());
        s.setRefreshTokenHash(Tokens.sha256Hex(refresh));
        s.setExpiresAt(clock.instant().plus(props.security().refreshTokenTtl()));
        if (client != null) {
            s.setIpAddress(client.ip());
            s.setUserAgent(client.userAgent());
            s.setDeviceLabel(client.deviceLabel());
        }
        repo.save(s);
        return new IssuedTokens(jwt.issueAccessToken(user.getId(), s.getId(), user.getFacultyId()),
                jwt.accessTokenTtlSeconds(), refresh, s.getId());
    }

    /** Rotaciona o refresh token. Um token antigo (já rotacionado) deixa de funcionar. */
    @Transactional
    public IssuedTokens refresh(String rawRefresh, ClientInfo client) {
        if (rawRefresh == null || rawRefresh.isBlank()) {
            throw ApiException.unauthorized("SESSION_EXPIRED", "Sessão expirada");
        }
        Instant now = clock.instant();
        UserSession s = repo.findByRefreshTokenHash(Tokens.sha256Hex(rawRefresh))
                .filter(x -> x.isActive(now))
                .orElseThrow(() -> ApiException.unauthorized("SESSION_EXPIRED", "Sessão expirada"));
        String refresh = Tokens.randomUrlToken(48);
        s.setRefreshTokenHash(Tokens.sha256Hex(refresh));
        s.setLastSeenAt(now);
        if (client != null) s.setIpAddress(client.ip());
        return new IssuedTokens(jwt.issueAccessToken(s.getUserId(), s.getId(), s.getFacultyId()),
                jwt.accessTokenTtlSeconds(), refresh, s.getId());
    }

    @Transactional
    public void revokeByRefreshToken(String rawRefresh) {
        if (rawRefresh == null || rawRefresh.isBlank()) return;
        repo.findByRefreshTokenHash(Tokens.sha256Hex(rawRefresh)).ifPresent(s -> revoke(s, "LOGOUT"));
    }

    @Transactional
    public void revoke(UserSession s, String reason) {
        if (s.getRevokedAt() == null) {
            s.setRevokedAt(clock.instant());
            s.setRevokedReason(reason);
        }
    }

    @Transactional
    public int revokeAll(UUID userId, UUID keepSessionId, String reason) {
        return repo.revokeAllForUser(userId, keepSessionId, clock.instant(), reason);
    }
}
