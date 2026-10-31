-- =====================================================================
-- V9 - Cadastro encadeado: o aluno pertence a um curso.
-- (Disciplinas passam a ser criadas dentro de um curso via API; o vínculo é a matriz curricular.)
-- =====================================================================
ALTER TABLE students ADD COLUMN course_id UUID REFERENCES courses(id);

-- Cadastros anteriores: curso da turma mais recente do aluno
UPDATE students s SET course_id = x.course_id
FROM (
    SELECT DISTINCT ON (cs.student_id) cs.student_id, c.course_id
    FROM class_students cs JOIN classes c ON c.id = cs.class_id JOIN academic_terms t ON t.id = c.term_id
    ORDER BY cs.student_id, t.start_date DESC
) x
WHERE x.student_id = s.id;

CREATE INDEX ix_students_course ON students(faculty_id, course_id);
CREATE INDEX ix_curriculum_subject ON curriculum(subject_id);
