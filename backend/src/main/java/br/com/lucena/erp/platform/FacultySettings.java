package br.com.lucena.erp.platform;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "faculty_settings")
@Getter
@Setter
public class FacultySettings {
    @Id
    private UUID facultyId;
    private String primaryColor = "#0B2545";
    private BigDecimal academicMinGrade = new BigDecimal("6.00");
    private BigDecimal academicMinAttendancePct = new BigDecimal("75.00");
    private BigDecimal lateFeePct = BigDecimal.ZERO;
    private BigDecimal monthlyInterestPct = BigDecimal.ZERO;
    private int sessionTimeoutMinutes = 30;
    private int dataRetentionYears = 20;
    private Instant updatedAt = Instant.now();
}
