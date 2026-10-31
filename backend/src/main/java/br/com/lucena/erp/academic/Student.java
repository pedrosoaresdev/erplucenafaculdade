package br.com.lucena.erp.academic;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "students")
@Getter
@Setter
public class Student {
    public enum Status { ACTIVE, LOCKED, CANCELLED, TRANSFERRED, GRADUATED, DROPPED_OUT }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private UUID facultyId;
    private UUID userId;
    private UUID courseId;
    private String registration;
    private String fullName;
    private String socialName;
    private String cpf;
    private String rg;
    private LocalDate birthDate;
    private String email;
    private String phone;
    private String addressStreet;
    private String addressNumber;
    private String addressDistrict;
    private String addressCity;
    private String addressState;
    private String addressZip;
    @Enumerated(EnumType.STRING)
    private Status status = Status.ACTIVE;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
