-- =====================================================================
-- V2 - Acadêmico: pessoas (alunos, professores, funcionários), cursos,
--      disciplinas, matriz curricular, turmas, matrículas, contratos,
--      avaliações, notas e frequência.
-- Todas as tabelas carregam faculty_id para isolamento multi-tenant.
-- =====================================================================

CREATE TABLE students (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    faculty_id        UUID NOT NULL REFERENCES faculties(id),
    user_id           UUID UNIQUE REFERENCES users(id),
    registration      VARCHAR(30)  NOT NULL,
    full_name         VARCHAR(200) NOT NULL,
    social_name       VARCHAR(200),
    cpf               VARCHAR(11)     NOT NULL CHECK (cpf ~ '^[0-9]{11}$'),
    rg                VARCHAR(20),
    birth_date        DATE,
    email             VARCHAR(200),
    phone             VARCHAR(30),
    address_street    VARCHAR(200),
    address_number    VARCHAR(20),
    address_district  VARCHAR(100),
    address_city      VARCHAR(100),
    address_state     VARCHAR(2),
    address_zip       VARCHAR(9),
    status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
                      CHECK (status IN ('ACTIVE','LOCKED','CANCELLED','TRANSFERRED','GRADUATED','DROPPED_OUT')),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (faculty_id, registration),
    UNIQUE (faculty_id, cpf)
);
CREATE INDEX ix_students_faculty_status ON students(faculty_id, status);
CREATE INDEX ix_students_name ON students(faculty_id, lower(full_name));

CREATE TABLE teachers (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    faculty_id    UUID NOT NULL REFERENCES faculties(id),
    user_id       UUID UNIQUE REFERENCES users(id),
    full_name     VARCHAR(200) NOT NULL,
    cpf           VARCHAR(11) NOT NULL CHECK (cpf ~ '^[0-9]{11}$'),
    email         VARCHAR(200),
    phone         VARCHAR(30),
    academic_degree VARCHAR(40),
    status        VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (faculty_id, cpf)
);
CREATE INDEX ix_teachers_faculty ON teachers(faculty_id);

CREATE TABLE employees (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    faculty_id    UUID NOT NULL REFERENCES faculties(id),
    user_id       UUID UNIQUE REFERENCES users(id),
    full_name     VARCHAR(200) NOT NULL,
    cpf           VARCHAR(11) NOT NULL CHECK (cpf ~ '^[0-9]{11}$'),
    department    VARCHAR(100),
    job_title     VARCHAR(100),
    status        VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (faculty_id, cpf)
);
CREATE INDEX ix_employees_faculty ON employees(faculty_id);

CREATE TABLE courses (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    faculty_id       UUID NOT NULL REFERENCES faculties(id),
    code             VARCHAR(20)  NOT NULL,
    name             VARCHAR(200) NOT NULL,
    degree           VARCHAR(30)  NOT NULL CHECK (degree IN ('BACHARELADO','LICENCIATURA','TECNOLOGO','POS_GRADUACAO','EXTENSAO')),
    modality         VARCHAR(20)  NOT NULL DEFAULT 'PRESENCIAL' CHECK (modality IN ('PRESENCIAL','EAD','HIBRIDO')),
    duration_semesters INTEGER    NOT NULL CHECK (duration_semesters BETWEEN 1 AND 20),
    total_hours      INTEGER      CHECK (total_hours > 0),
    monthly_fee      NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (monthly_fee >= 0),
    coordinator_id   UUID REFERENCES teachers(id),
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (faculty_id, code)
);

CREATE TABLE subjects (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    faculty_id   UUID NOT NULL REFERENCES faculties(id),
    code         VARCHAR(20)  NOT NULL,
    name         VARCHAR(200) NOT NULL,
    workload_hours INTEGER    NOT NULL CHECK (workload_hours > 0),
    credits      INTEGER      NOT NULL CHECK (credits >= 0),
    syllabus     TEXT,
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (faculty_id, code)
);

-- Matriz curricular (curso x disciplina x semestre recomendado)
CREATE TABLE curriculum (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    faculty_id   UUID NOT NULL REFERENCES faculties(id),
    course_id    UUID NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    subject_id   UUID NOT NULL REFERENCES subjects(id),
    semester     INTEGER NOT NULL CHECK (semester BETWEEN 1 AND 20),
    mandatory    BOOLEAN NOT NULL DEFAULT TRUE,
    prerequisite_subject_id UUID REFERENCES subjects(id),
    UNIQUE (course_id, subject_id)
);
CREATE INDEX ix_curriculum_faculty ON curriculum(faculty_id);

CREATE TABLE academic_terms (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    faculty_id  UUID NOT NULL REFERENCES faculties(id),
    code        VARCHAR(10) NOT NULL CHECK (code ~ '^[0-9]{4}\.[12]$'),
    start_date  DATE NOT NULL,
    end_date    DATE NOT NULL,
    status      VARCHAR(15) NOT NULL DEFAULT 'PLANNED' CHECK (status IN ('PLANNED','OPEN','CLOSED')),
    CHECK (end_date > start_date),
    UNIQUE (faculty_id, code)
);

CREATE TABLE classes (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    faculty_id  UUID NOT NULL REFERENCES faculties(id),
    course_id   UUID NOT NULL REFERENCES courses(id),
    term_id     UUID NOT NULL REFERENCES academic_terms(id),
    code        VARCHAR(30) NOT NULL,
    semester    INTEGER NOT NULL CHECK (semester BETWEEN 1 AND 20),
    shift       VARCHAR(10) NOT NULL CHECK (shift IN ('MANHA','TARDE','NOITE','INTEGRAL','EAD')),
    capacity    INTEGER NOT NULL CHECK (capacity > 0),
    room        VARCHAR(40),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (faculty_id, term_id, code)
);

CREATE TABLE class_students (
    class_id    UUID NOT NULL REFERENCES classes(id) ON DELETE CASCADE,
    student_id  UUID NOT NULL REFERENCES students(id),
    faculty_id  UUID NOT NULL REFERENCES faculties(id),
    joined_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (class_id, student_id)
);

-- Oferta de disciplina na turma, com professor responsável e horário
CREATE TABLE class_subjects (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    faculty_id  UUID NOT NULL REFERENCES faculties(id),
    class_id    UUID NOT NULL REFERENCES classes(id) ON DELETE CASCADE,
    subject_id  UUID NOT NULL REFERENCES subjects(id),
    teacher_id  UUID REFERENCES teachers(id),
    weekday     SMALLINT CHECK (weekday BETWEEN 1 AND 7),
    start_time  TIME,
    end_time    TIME,
    teaching_plan TEXT,
    UNIQUE (class_id, subject_id)
);
CREATE INDEX ix_class_subjects_teacher ON class_subjects(teacher_id);

CREATE TABLE enrollments (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    faculty_id  UUID NOT NULL REFERENCES faculties(id),
    student_id  UUID NOT NULL REFERENCES students(id),
    course_id   UUID NOT NULL REFERENCES courses(id),
    term_id     UUID NOT NULL REFERENCES academic_terms(id),
    class_id    UUID REFERENCES classes(id),
    kind        VARCHAR(15) NOT NULL DEFAULT 'MATRICULA' CHECK (kind IN ('MATRICULA','REMATRICULA','TRANSFERENCIA')),
    status      VARCHAR(15) NOT NULL DEFAULT 'PENDING'
                CHECK (status IN ('PENDING','ACTIVE','LOCKED','CANCELLED','COMPLETED')),
    current_semester INTEGER NOT NULL DEFAULT 1 CHECK (current_semester BETWEEN 1 AND 20),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (student_id, term_id)
);
CREATE INDEX ix_enrollments_faculty ON enrollments(faculty_id, status);

CREATE TABLE enrollment_subjects (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    faculty_id       UUID NOT NULL REFERENCES faculties(id),
    enrollment_id    UUID NOT NULL REFERENCES enrollments(id) ON DELETE CASCADE,
    class_subject_id UUID NOT NULL REFERENCES class_subjects(id),
    status           VARCHAR(15) NOT NULL DEFAULT 'IN_PROGRESS'
                     CHECK (status IN ('IN_PROGRESS','APPROVED','FAILED_GRADE','FAILED_ATTENDANCE','EXEMPT','WITHDRAWN')),
    final_grade      NUMERIC(4,2) CHECK (final_grade BETWEEN 0 AND 10),
    attendance_pct   NUMERIC(5,2) CHECK (attendance_pct BETWEEN 0 AND 100),
    UNIQUE (enrollment_id, class_subject_id)
);

CREATE TABLE contracts (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    faculty_id    UUID NOT NULL REFERENCES faculties(id),
    enrollment_id UUID NOT NULL REFERENCES enrollments(id),
    version       INTEGER NOT NULL DEFAULT 1,
    content_hash  VARCHAR(64) NOT NULL,
    document_url  VARCHAR(500),
    status        VARCHAR(15) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','SIGNED','CANCELLED')),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    signed_at     TIMESTAMPTZ
);

CREATE TABLE assessments (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    faculty_id       UUID NOT NULL REFERENCES faculties(id),
    class_subject_id UUID NOT NULL REFERENCES class_subjects(id) ON DELETE CASCADE,
    title            VARCHAR(120) NOT NULL,
    kind             VARCHAR(15) NOT NULL CHECK (kind IN ('PROVA','TRABALHO','SEMINARIO','RECUPERACAO','OUTRO')),
    weight           NUMERIC(5,2) NOT NULL DEFAULT 1 CHECK (weight > 0),
    max_score        NUMERIC(5,2) NOT NULL DEFAULT 10 CHECK (max_score > 0),
    due_date         DATE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE grades (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    faculty_id     UUID NOT NULL REFERENCES faculties(id),
    assessment_id  UUID NOT NULL REFERENCES assessments(id) ON DELETE CASCADE,
    student_id     UUID NOT NULL REFERENCES students(id),
    score          NUMERIC(5,2) NOT NULL CHECK (score >= 0),
    updated_by     UUID REFERENCES users(id),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (assessment_id, student_id)
);

CREATE TABLE attendance_sessions (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    faculty_id       UUID NOT NULL REFERENCES faculties(id),
    class_subject_id UUID NOT NULL REFERENCES class_subjects(id) ON DELETE CASCADE,
    session_date     DATE NOT NULL,
    lessons          SMALLINT NOT NULL DEFAULT 1 CHECK (lessons BETWEEN 1 AND 10),
    content          TEXT,
    created_by       UUID REFERENCES users(id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (class_subject_id, session_date)
);

CREATE TABLE attendance (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    faculty_id  UUID NOT NULL REFERENCES faculties(id),
    session_id  UUID NOT NULL REFERENCES attendance_sessions(id) ON DELETE CASCADE,
    student_id  UUID NOT NULL REFERENCES students(id),
    absences    SMALLINT NOT NULL DEFAULT 0 CHECK (absences >= 0),
    justified   BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (session_id, student_id)
);
