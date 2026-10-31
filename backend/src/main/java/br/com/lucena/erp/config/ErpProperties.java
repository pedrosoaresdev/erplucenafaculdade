package br.com.lucena.erp.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "erp")
public record ErpProperties(Security security, Bootstrap bootstrap, Cors cors) {

    public record Security(
            /** Chave HMAC (Base64, >= 32 bytes) para assinar access tokens JWT. */
            @NotBlank String jwtSecret,
            /** Chave AES-256 (Base64, 32 bytes) para cifrar segredos TOTP. */
            @NotBlank String encryptionKey,
            Duration accessTokenTtl,
            Duration refreshTokenTtl,
            Duration challengeTtl,
            Duration stepUpTtl,
            @NotBlank String totpIssuer,
            boolean cookieSecure,
            @Min(1) @Max(20) int maxFailedLogins,
            Duration lockoutDuration,
            @Min(1) @Max(10) int maxChallengeAttempts,
            @Min(5) int authRequestsPerMinute,
            @Min(4) @Max(20) int recoveryCodeCount,
            /** Consulta opcional de senhas vazadas (HIBP k-anonymity). */
            boolean breachedPasswordCheck) {
    }

    public record Bootstrap(String masterUsername, String masterPassword, String masterEmail, String masterName) {
    }

    public record Cors(List<String> allowedOrigins) {
    }
}
