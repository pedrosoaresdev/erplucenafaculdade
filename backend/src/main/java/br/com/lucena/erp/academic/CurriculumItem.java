package br.com.lucena.erp.academic;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "curriculum")
@Getter
@Setter
public class CurriculumItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private UUID facultyId;
    private UUID courseId;
    private UUID subjectId;
    private int semester;
    private boolean mandatory = true;
    private UUID prerequisiteSubjectId;
}
