package br.com.lucena.erp.me;

import br.com.lucena.erp.auth.AuthChallenge;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class MeDtos {
    private MeDtos() {
    }

    public record Profile(UUID id, String username, String fullName, String socialName, String email, String phone,
                          String photoUrl, String status, Instant createdAt, Instant lastLoginAt,
                          boolean platformAdmin, boolean mustChangePassword, boolean mfaEnabled,
                          FacultyInfo faculty, List<String> roles, Set<String> permissions, String registration) {
    }

    public record FacultyInfo(UUID id, String slug, String name, String logoUrl) {
    }

    /** Dados não sensíveis: alteração direta. */
    public record UpdateProfile(@Size(max = 200) String socialName, @Size(max = 30) String phone,
                                @Size(max = 500) String photoUrl) {
    }

    /** E-mail é dado sensível: exige step-up (senha + TOTP). */
    public record UpdateEmail(@NotBlank String stepUpToken, @NotBlank @Email @Size(max = 200) String email) {
    }

    public enum StepUpPurpose {
        PASSWORD_CHANGE(AuthChallenge.Purpose.STEPUP_PASSWORD_CHANGE),
        MFA_ROTATE(AuthChallenge.Purpose.STEPUP_MFA_ROTATE),
        MFA_DISABLE(AuthChallenge.Purpose.STEPUP_MFA_DISABLE),
        RECOVERY_CODES(AuthChallenge.Purpose.STEPUP_RECOVERY_CODES),
        PROFILE_SENSITIVE(AuthChallenge.Purpose.STEPUP_PROFILE_SENSITIVE);

        public final AuthChallenge.Purpose challengePurpose;

        StepUpPurpose(AuthChallenge.Purpose p) {
            this.challengePurpose = p;
        }
    }

    public record StepUpRequest(@NotBlank @Size(max = 128) String currentPassword,
                                @NotBlank @Size(max = 12) String totpCode,
                                @NotNull StepUpPurpose purpose) {
    }

    public record StepUpResponse(String stepUpToken, long expiresIn) {
    }

    public record ChangePassword(@NotBlank String stepUpToken,
                                 @NotBlank @Size(max = 128) String newPassword,
                                 @NotBlank @Size(max = 128) String confirmPassword) {
    }

    public record StepUpOnly(@NotBlank String stepUpToken) {
    }

    public record CodeOnly(@NotBlank @Size(max = 12) String code) {
    }

    public record MfaStatus(boolean enabled, Instant activatedAt, long recoveryCodesRemaining, boolean rotationPending) {
    }

    public record MfaEnrollment(String secret, String otpauthUri, String qrCodeDataUrl, String accountLabel) {
    }

    public record RecoveryCodes(List<String> codes) {
    }

    public record SessionView(UUID id, String deviceLabel, String ipAddress, String userAgent, Instant createdAt,
                              Instant lastSeenAt, Instant expiresAt, boolean current) {
    }

    public record AccessView(String event, boolean success, String ipAddress, String userAgent, String detail, Instant createdAt) {
    }

    public record NotificationView(UUID id, String category, String title, String body, String link, Instant readAt, Instant createdAt) {
    }
}
