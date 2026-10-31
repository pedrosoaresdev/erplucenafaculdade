-- =====================================================================
-- V6 - Fase 4: regras acadêmicas adicionais.
-- =====================================================================

-- Numeração sequencial de matrículas por faculdade e ano (atualização atômica).
CREATE TABLE registration_counters (
    faculty_id  UUID NOT NULL REFERENCES faculties(id) ON DELETE CASCADE,
    year        INTEGER NOT NULL,
    last_value  INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (faculty_id, year)
);

ALTER TABLE employees ADD COLUMN email VARCHAR(200);
ALTER TABLE employees ADD COLUMN phone VARCHAR(30);
ALTER TABLE teachers ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE courses ADD COLUMN description TEXT;
ALTER TABLE classes ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

-- Um aluno pertence a no máximo uma turma por período letivo.
ALTER TABLE class_students ADD COLUMN term_id UUID REFERENCES academic_terms(id);
UPDATE class_students cs SET term_id = c.term_id FROM classes c WHERE c.id = cs.class_id;
ALTER TABLE class_students ALTER COLUMN term_id SET NOT NULL;
CREATE UNIQUE INDEX ux_class_students_term ON class_students(student_id, term_id);

-- Horários: término depois do início; ambos informados ou nenhum.
ALTER TABLE class_subjects ADD CONSTRAINT ck_class_subjects_time
    CHECK ((weekday IS NULL AND start_time IS NULL AND end_time IS NULL)
        OR (weekday IS NOT NULL AND start_time IS NOT NULL AND end_time IS NOT NULL AND end_time > start_time));

-- Disciplina não pode ser pré-requisito de si mesma.
ALTER TABLE curriculum ADD CONSTRAINT ck_curriculum_prereq CHECK (prerequisite_subject_id IS NULL OR prerequisite_subject_id <> subject_id);

CREATE INDEX ix_courses_faculty ON courses(faculty_id, active);
CREATE INDEX ix_subjects_faculty ON subjects(faculty_id, active);
CREATE INDEX ix_classes_faculty_term ON classes(faculty_id, term_id);
CREATE INDEX ix_class_subjects_class ON class_subjects(class_id);
CREATE INDEX ix_class_students_student ON class_students(student_id);
