package br.com.lucena.erp.platform;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class FacultyDtos {
    private FacultyDtos() {
    }

    public record FacultyRequest(
            @NotBlank @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "use letras minúsculas, números e hífen") @Size(max = 60) String slug,
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 200) String legalName,
            @Size(max = 200) String tradeName,
            @NotBlank String cnpj,
            @NotBlank @Email @Size(max = 200) String email,
            @Size(max = 30) String phone,
            @Size(max = 200) String addressStreet,
            @Size(max = 20) String addressNumber,
            @Size(max = 100) String addressComplement,
            @Size(max = 100) String addressDistrict,
            @Size(max = 100) String addressCity,
            @Pattern(regexp = "^[A-Z]{2}$", message = "UF com 2 letras maiúsculas") String addressState,
            @Pattern(regexp = "^\\d{5}-?\\d{3}$", message = "CEP inválido") String addressZip,
            @Size(max = 500) @Pattern(regexp = "^(https://.*)?$", message = "logotipo deve ser uma URL https") String logoUrl,
            UUID planId,
            @Valid InitialAdmin initialAdmin) {
    }

    /** Diretor Geral criado junto com a faculdade (senha temporária exibida uma única vez). */
    public record InitialAdmin(@NotBlank @Pattern(regexp = "^[A-Za-z0-9._-]{3,60}$") String username,
                               @NotBlank @Size(max = 200) String fullName,
                               @NotBlank @Email String email) {
    }

    public record FacultyView(UUID id, String slug, String name, String legalName, String tradeName, String cnpj,
                              String email, String phone, String addressStreet, String addressNumber,
                              String addressComplement, String addressDistrict, String addressCity, String addressState,
                              String addressZip, String logoUrl, String status, String statusReason,
                              UUID planId, String planName, Instant createdAt, long users, long students, long courses) {
    }

    public record CreatedFaculty(FacultyView faculty, String initialAdminUsername, String initialAdminTemporaryPassword) {
    }

    public enum StatusAction { ACTIVATE, SUSPEND, REACTIVATE, ARCHIVE }

    public record StatusChange(@NotNull StatusAction action, @Size(max = 500) String reason) {
    }

    public record PublicFaculty(String slug, String name, String logoUrl, String primaryColor) {
    }

    public record PlanRequest(@NotBlank @Pattern(regexp = "^[A-Z0-9_]{2,40}$") String code, @NotBlank @Size(max = 120) String name,
                              @Positive Integer maxStudents, @Positive Integer maxUsers,
                              @NotNull @PositiveOrZero BigDecimal monthlyPrice, boolean active) {
    }

    public record PlanView(UUID id, String code, String name, Integer maxStudents, Integer maxUsers, BigDecimal monthlyPrice, boolean active) {
        static PlanView of(Plan p) {
            return new PlanView(p.getId(), p.getCode(), p.getName(), p.getMaxStudents(), p.getMaxUsers(), p.getMonthlyPrice(), p.isActive());
        }
    }

    public record SettingsView(String primaryColor, BigDecimal academicMinGrade, BigDecimal academicMinAttendancePct,
                               BigDecimal lateFeePct, BigDecimal monthlyInterestPct, int sessionTimeoutMinutes, int dataRetentionYears) {
    }

    public record SettingsRequest(@NotBlank @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String primaryColor,
                                  @NotNull @DecimalMin("0") @DecimalMax("10") BigDecimal academicMinGrade,
                                  @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal academicMinAttendancePct,
                                  @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal lateFeePct,
                                  @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal monthlyInterestPct,
                                  @Min(5) @Max(1440) int sessionTimeoutMinutes,
                                  @Min(1) @Max(100) int dataRetentionYears) {
    }
}
