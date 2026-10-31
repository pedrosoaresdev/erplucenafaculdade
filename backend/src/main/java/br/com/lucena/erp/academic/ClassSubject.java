package br.com.lucena.erp.academic;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "class_subjects")
@Getter
@Setter
public class ClassSubject {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private UUID facultyId;
    private UUID classId;
    private UUID subjectId;
    private UUID teacherId;
    private Short weekday;
    private LocalTime startTime;
    private LocalTime endTime;
    private String teachingPlan;
}
