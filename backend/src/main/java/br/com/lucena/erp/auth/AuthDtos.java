package br.com.lucena.erp.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class AuthDtos {
    private AuthDtos() {
    }

    /** faculty = slug da faculdade (ausente para o login Master da plataforma). */
    public record LoginRequest(@Size(max = 60) String faculty,
                               @NotBlank @Size(max = 120) String identifier,
                               @NotBlank @Size(max = 128) String password) {
    }

    public enum NextStep { MFA_SETUP_REQUIRED, MFA_REQUIRED }

    public record LoginResponse(NextStep nextStep, String challengeToken, long expiresIn, String displayName) {
    }

    public record ChallengeRequest(@NotBlank String challengeToken) {
    }

    public record CodeRequest(@NotBlank String challengeToken, @NotBlank @Size(max = 12) String code) {
    }

    public record RecoveryLoginRequest(@NotBlank String challengeToken, @NotBlank @Size(max = 20) String recoveryCode) {
    }

    public record MfaSetupResponse(String secret, String otpauthUri, String qrCodeDataUrl, String accountLabel) {
    }

    /** recoveryCodes só vem preenchido na ativação (exibição única). */
    public record TokenResponse(String accessToken, String tokenType, long expiresIn, boolean mustChangePassword,
                                List<String> recoveryCodes, Long remainingRecoveryCodes) {
    }
}
