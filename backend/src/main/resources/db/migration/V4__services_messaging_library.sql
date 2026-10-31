-- =====================================================================
-- V4 - Requerimentos, documentos/assinaturas, mensagens e biblioteca.
-- =====================================================================

CREATE TABLE request_types (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    faculty_id        UUID NOT NULL REFERENCES faculties(id),
    code              VARCHAR(20)  NOT NULL,
    description       VARCHAR(200) NOT NULL,
    price             NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (price >= 0),
    free_of_charge    BOOLEAN NOT NULL DEFAULT FALSE,
    deadline_days     INTEGER NOT NULL CHECK (deadline_days > 0),
    responsible_sector VARCHAR(60) NOT NULL,
    required_documents TEXT,
    requires_approval BOOLEAN NOT NULL DEFAULT TRUE,
    active            BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (faculty_id, code),
    CHECK (free_of_charge OR price > 0)
);

CREATE SEQUENCE request_protocol_seq;

CREATE TABLE requests (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    faculty_id      UUID NOT NULL REFERENCES faculties(id),
    protocol        VARCHAR(30) NOT NULL UNIQUE,
    request_type_id UUID NOT NULL REFERENCES request_types(id),
    student_id      UUID NOT NULL REFERENCES students(id),
    title_id        UUID REFERENCES financial_titles(id),
    status          VARCHAR(25) NOT NULL DEFAULT 'SOLICITADO'
                    CHECK (status IN ('SOLICITADO','AGUARDANDO_PAGAMENTO','PAGAMENTO_CONFIRMADO','EM_ANALISE','DEFERIDO','INDEFERIDO','CONCLUIDO','CANCELADO')),
    notes           TEXT,
    decision        TEXT,
    deadline_at     DATE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_requests_faculty_status ON requests(faculty_id, status);
CREATE INDEX ix_requests_student ON requests(student_id);

CREATE TABLE request_history (
    id          BIGSERIAL PRIMARY KEY,
    faculty_id  UUID NOT NULL REFERENCES faculties(id),
    request_id  UUID NOT NULL REFERENCES requests(id) ON DELETE CASCADE,
    from_status VARCHAR(25),
    to_status   VARCHAR(25) NOT NULL,
    comment     TEXT,
    actor_id    UUID REFERENCES users(id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE documents (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    faculty_id    UUID NOT NULL REFERENCES faculties(id),
    owner_user_id UUID REFERENCES users(id),
    student_id    UUID REFERENCES students(id),
    category      VARCHAR(30) NOT NULL CHECK (category IN ('DECLARACAO_MATRICULA','DECLARACAO_FREQUENCIA','HISTORICO','EMENTA','CONTRATO','CERTIFICADO','COMPROVANTE','ANEXO','OUTRO')),
    file_name     VARCHAR(255) NOT NULL,
    content_type  VARCHAR(100) NOT NULL,
    size_bytes    BIGINT NOT NULL CHECK (size_bytes > 0 AND size_bytes <= 26214400),
    storage_path  VARCHAR(500) NOT NULL,
    sha256        VARCHAR(64) NOT NULL,
    verification_code VARCHAR(40) UNIQUE,
    created_by    UUID REFERENCES users(id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_documents_faculty_student ON documents(faculty_id, student_id);

CREATE TABLE signatures (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    faculty_id   UUID NOT NULL REFERENCES faculties(id),
    document_id  UUID NOT NULL REFERENCES documents(id),
    signer_id    UUID NOT NULL REFERENCES users(id),
    signed_hash  VARCHAR(64) NOT NULL,
    ip_address   VARCHAR(64),
    signed_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (document_id, signer_id)
);

CREATE TABLE conversations (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    faculty_id  UUID NOT NULL REFERENCES faculties(id),
    kind        VARCHAR(15) NOT NULL CHECK (kind IN ('DIRECT','GROUP','CLASS','SUBJECT','BROADCAST')),
    subject     VARCHAR(200),
    class_id    UUID REFERENCES classes(id),
    class_subject_id UUID REFERENCES class_subjects(id),
    created_by  UUID REFERENCES users(id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_conversations_faculty ON conversations(faculty_id);

CREATE TABLE conversation_participants (
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users(id),
    faculty_id      UUID NOT NULL REFERENCES faculties(id),
    last_read_at    TIMESTAMPTZ,
    joined_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (conversation_id, user_id)
);
CREATE INDEX ix_conv_participants_user ON conversation_participants(user_id);

CREATE TABLE messages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    faculty_id      UUID NOT NULL REFERENCES faculties(id),
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    sender_id       UUID NOT NULL REFERENCES users(id),
    body            TEXT NOT NULL CHECK (length(body) <= 10000),
    attachment_document_id UUID REFERENCES documents(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_messages_conversation ON messages(conversation_id, created_at);

CREATE TABLE books (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    faculty_id    UUID NOT NULL REFERENCES faculties(id),
    isbn          VARCHAR(13) CHECK (isbn ~ '^([0-9]{9}[0-9X]|[0-9]{13})$'),
    title         VARCHAR(300) NOT NULL,
    authors       VARCHAR(500) NOT NULL,
    publisher     VARCHAR(200),
    publication_year INTEGER,
    category      VARCHAR(100),
    total_copies  INTEGER NOT NULL DEFAULT 1 CHECK (total_copies >= 0),
    digital_url   VARCHAR(500),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (faculty_id, isbn)
);
CREATE INDEX ix_books_title ON books(faculty_id, lower(title));

CREATE TABLE loans (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    faculty_id  UUID NOT NULL REFERENCES faculties(id),
    book_id     UUID NOT NULL REFERENCES books(id),
    user_id     UUID NOT NULL REFERENCES users(id),
    loaned_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    due_at      DATE NOT NULL,
    returned_at TIMESTAMPTZ,
    registered_by UUID REFERENCES users(id)
);
CREATE INDEX ix_loans_open ON loans(book_id) WHERE returned_at IS NULL;

CREATE TABLE reservations (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    faculty_id  UUID NOT NULL REFERENCES faculties(id),
    book_id     UUID NOT NULL REFERENCES books(id),
    user_id     UUID NOT NULL REFERENCES users(id),
    status      VARCHAR(15) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','FULFILLED','CANCELLED','EXPIRED')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_reservations_active ON reservations(book_id, user_id) WHERE status = 'ACTIVE';

CREATE TABLE book_favorites (
    book_id    UUID NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (book_id, user_id)
);

CREATE TABLE annotations (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    faculty_id  UUID NOT NULL REFERENCES faculties(id),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    book_id     UUID REFERENCES books(id),
    title       VARCHAR(200) NOT NULL,
    body        TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_annotations_user ON annotations(user_id);
