-- =====================================================================
-- V7 - Fase 5: diário de classe (avaliações, notas e frequência).
-- =====================================================================
ALTER TABLE assessments ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE attendance_sessions ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE INDEX ix_assessments_class_subject ON assessments(class_subject_id);
CREATE INDEX ix_grades_student ON grades(student_id);
CREATE INDEX ix_attendance_student ON attendance(student_id);
CREATE INDEX ix_attendance_sessions_cs ON attendance_sessions(class_subject_id, session_date);
CREATE INDEX ix_teachers_user ON teachers(user_id);
CREATE INDEX ix_students_user ON students(user_id);
