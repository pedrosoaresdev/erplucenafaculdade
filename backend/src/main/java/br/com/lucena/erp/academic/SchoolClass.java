package br.com.lucena.erp.academic;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "classes")
@Getter
@Setter
public class SchoolClass {
    public enum Shift { MANHA, TARDE, NOITE, INTEGRAL, EAD }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private UUID facultyId;
    private UUID courseId;
    private UUID termId;
    private String code;
    private int semester;
    @Enumerated(EnumType.STRING)
    private Shift shift;
    private int capacity;
    private String room;
    private boolean active = true;
    private Instant createdAt = Instant.now();
}
