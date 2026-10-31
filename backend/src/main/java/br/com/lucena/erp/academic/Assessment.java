package br.com.lucena.erp.academic;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "assessments")
@Getter
@Setter
public class Assessment {
    public enum Kind { PROVA, TRABALHO, SEMINARIO, RECUPERACAO, OUTRO }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private UUID facultyId;
    private UUID classSubjectId;
    private String title;
    @Enumerated(EnumType.STRING)
    private Kind kind;
    private BigDecimal weight = BigDecimal.ONE;
    private BigDecimal maxScore = BigDecimal.TEN;
    private LocalDate dueDate;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
