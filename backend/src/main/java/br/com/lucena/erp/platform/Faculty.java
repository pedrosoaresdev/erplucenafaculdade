package br.com.lucena.erp.platform;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "faculties")
@Getter
@Setter
public class Faculty {
    public enum Status { ACTIVE, SUSPENDED, ARCHIVED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private String slug;
    private String name;
    private String legalName;
    private String tradeName;
    private String cnpj;
    private String email;
    private String phone;
    private String addressStreet;
    private String addressNumber;
    private String addressComplement;
    private String addressDistrict;
    private String addressCity;
    private String addressState;
    private String addressZip;
    private String logoUrl;
    @Enumerated(EnumType.STRING)
    private Status status = Status.ACTIVE;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id")
    private Plan plan;
    private String statusReason;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
    private Instant archivedAt;

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
