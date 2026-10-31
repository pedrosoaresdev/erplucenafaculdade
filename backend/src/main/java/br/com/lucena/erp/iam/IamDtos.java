package br.com.lucena.erp.iam;

import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class IamDtos {
    private IamDtos() {
    }

    public record CreateUser(@NotBlank @Pattern(regexp = "^[A-Za-z0-9._-]{3,60}$", message = "3 a 60 caracteres: letras, números, ponto, hífen ou sublinhado") String username,
                             @NotBlank @Size(max = 200) String fullName,
                             @NotBlank @Email @Size(max = 200) String email,
                             @Size(max = 30) String phone,
                             @NotEmpty Set<UUID> roleIds) {
    }

    public record UpdateUser(@NotBlank @Size(max = 200) String fullName,
                             @NotBlank @Email @Size(max = 200) String email,
                             @Size(max = 30) String phone) {
    }

    public record SetRoles(@NotEmpty Set<UUID> roleIds) {
    }

    public record SetStatus(@NotNull User.Status status, @Size(max = 300) String reason) {
    }

    /** Recuperação de conta: exige motivo e registro da verificação de identidade realizada. */
    public record MfaReset(@NotBlank @Size(min = 10, max = 500) String reason,
                           @NotBlank @Size(min = 5, max = 500) String identityVerification) {
    }

    public record UserView(UUID id, UUID facultyId, String username, String fullName, String email, String phone,
                           String status, boolean mfaEnabled, boolean mustChangePassword, boolean locked,
                           Instant lastLoginAt, Instant createdAt, List<RoleRef> roles) {
        public static UserView of(User u, Instant now) {
            return new UserView(u.getId(), u.getFacultyId(), u.getUsername(), u.getFullName(), u.getEmail(), u.getPhone(),
                    u.getStatus().name(), u.isMfaEnabled(), u.isMustChangePassword(), u.isLocked(now), u.getLastLoginAt(),
                    u.getCreatedAt(), u.getRoles().stream().map(r -> new RoleRef(r.getId(), r.getCode(), r.getName())).toList());
        }
    }

    public record RoleRef(UUID id, String code, String name) {
    }

    public record TemporaryCredential(String username, String temporaryPassword) {
    }

    public record CreatedUser(UserView user, String temporaryPassword) {
    }

    public record RoleRequest(@NotBlank @Pattern(regexp = "^[A-Z0-9_]{2,60}$", message = "use MAIÚSCULAS, números e _") String code,
                              @NotBlank @Size(max = 120) String name,
                              @Size(max = 255) String description,
                              @NotNull Set<String> permissions) {
    }

    public record RoleView(UUID id, String code, String name, String description, boolean systemRole,
                           List<String> permissions, long users) {
    }

    public record PermissionView(String code, String module, String description) {
    }
}
