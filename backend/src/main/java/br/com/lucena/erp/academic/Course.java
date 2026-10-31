package br.com.lucena.erp.academic;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "courses")
@Getter
@Setter
public class Course {
    public enum Degree { BACHARELADO, LICENCIATURA, TECNOLOGO, POS_GRADUACAO, EXTENSAO }
    public enum Modality { PRESENCIAL, EAD, HIBRIDO }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private UUID facultyId;
    private String code;
    private String name;
    private String description;
    @Enumerated(EnumType.STRING)
    private Degree degree;
    @Enumerated(EnumType.STRING)
    private Modality modality = Modality.PRESENCIAL;
    private int durationSemesters;
    private Integer totalHours;
    private BigDecimal monthlyFee = BigDecimal.ZERO;
    private UUID coordinatorId;
    private boolean active = true;
    private Instant createdAt = Instant.now();
}
