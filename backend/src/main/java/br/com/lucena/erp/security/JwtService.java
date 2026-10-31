package br.com.lucena.erp.security;

import br.com.lucena.erp.config.ErpProperties;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/** Emite access tokens JWT (HS256) de curta duração vinculados a uma sessão do servidor. */
@Service
public class JwtService {
    public static final String ISSUER = "erp-lucena";
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final ErpProperties props;
    private final Clock clock;

    public JwtService(ErpProperties props, Clock clock) {
        byte[] raw = Base64.getDecoder().decode(props.security().jwtSecret());
        if (raw.length < 32) {
            throw new IllegalStateException("erp.security.jwt-secret deve ter no mínimo 32 bytes (Base64)");
        }
        SecretKey key = new SecretKeySpec(raw, "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        NimbusJwtDecoder dec = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        dec.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
        this.decoder = dec;
        this.props = props;
        this.clock = clock;
    }

    public String issueAccessToken(UUID userId, UUID sessionId, UUID facultyId) {
        Instant now = clock.instant();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plus(props.security().accessTokenTtl()))
                .id(UUID.randomUUID().toString())
                .claim("sid", sessionId.toString())
                .claim("typ", "access");
        if (facultyId != null) claims.claim("fid", facultyId.toString());
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();
    }

    public JwtDecoder decoder() {
        return decoder;
    }

    public long accessTokenTtlSeconds() {
        return props.security().accessTokenTtl().toSeconds();
    }
}
