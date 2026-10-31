package br.com.lucena.erp.academic;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "employees")
@Getter
@Setter
public class Employee {
    public enum Status { ACTIVE, INACTIVE }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private UUID facultyId;
    private UUID userId;
    private String fullName;
    private String cpf;
    private String email;
    private String phone;
    private String department;
    private String jobTitle;
    @Enumerated(EnumType.STRING)
    private Status status = Status.ACTIVE;
    private Instant createdAt = Instant.now();
}
