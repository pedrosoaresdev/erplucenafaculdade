package br.com.lucena.erp.academic;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Aula registrada no diário de classe (data, quantidade de aulas e conteúdo ministrado). */
@Entity
@Table(name = "attendance_sessions")
@Getter
@Setter
public class AttendanceSession {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private UUID facultyId;
    private UUID classSubjectId;
    private LocalDate sessionDate;
    private short lessons = 1;
    private String content;
    private UUID createdBy;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
