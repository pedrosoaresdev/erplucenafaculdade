-- =====================================================================
-- V3 - Financeiro. Valores em NUMERIC(14,2). Pagamentos confirmados
-- somente por webhook validado (provider_event_id único = idempotência).
-- =====================================================================

CREATE TABLE cost_centers (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    faculty_id  UUID NOT NULL REFERENCES faculties(id),
    code        VARCHAR(20)  NOT NULL,
    name        VARCHAR(120) NOT NULL,
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (faculty_id, code)
);

CREATE TABLE scholarships (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    faculty_id    UUID NOT NULL REFERENCES faculties(id),
    student_id    UUID NOT NULL REFERENCES students(id),
    name          VARCHAR(120) NOT NULL,
    percentage    NUMERIC(5,2) NOT NULL CHECK (percentage > 0 AND percentage <= 100),
    valid_from    DATE NOT NULL,
    valid_until   DATE,
    approved_by   UUID REFERENCES users(id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Título financeiro = obrigação (mensalidade, requerimento, acordo...)
CREATE TABLE financial_titles (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    faculty_id      UUID NOT NULL REFERENCES faculties(id),
    student_id      UUID NOT NULL REFERENCES students(id),
    enrollment_id   UUID REFERENCES enrollments(id),
    cost_center_id  UUID REFERENCES cost_centers(id),
    kind            VARCHAR(20) NOT NULL CHECK (kind IN ('MENSALIDADE','MATRICULA','REQUERIMENTO','ACORDO','OUTRO')),
    description     VARCHAR(200) NOT NULL,
    reference_month DATE,
    installment     INTEGER,
    installments_total INTEGER,
    original_amount NUMERIC(14,2) NOT NULL CHECK (original_amount > 0),
    discount_amount NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (discount_amount >= 0),
    due_date        DATE NOT NULL,
    status          VARCHAR(15) NOT NULL DEFAULT 'OPEN'
                    CHECK (status IN ('OPEN','PAID','PARTIALLY_PAID','CANCELLED','RENEGOTIATED','REFUNDED')),
    renegotiated_into UUID REFERENCES financial_titles(id),
    created_by      UUID REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (discount_amount < original_amount)
);
CREATE INDEX ix_titles_faculty_status_due ON financial_titles(faculty_id, status, due_date);
CREATE INDEX ix_titles_student ON financial_titles(student_id);

CREATE TABLE discounts (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    faculty_id  UUID NOT NULL REFERENCES faculties(id),
    title_id    UUID NOT NULL REFERENCES financial_titles(id) ON DELETE CASCADE,
    kind        VARCHAR(20) NOT NULL CHECK (kind IN ('PONTUALIDADE','BOLSA','CONVENIO','ABATIMENTO','OUTRO')),
    amount      NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    reason      VARCHAR(255),
    granted_by  UUID REFERENCES users(id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Cobrança emitida no provedor (boleto/Pix) para um título
CREATE TABLE charges (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    faculty_id          UUID NOT NULL REFERENCES faculties(id),
    title_id            UUID NOT NULL REFERENCES financial_titles(id),
    method              VARCHAR(10) NOT NULL CHECK (method IN ('BOLETO','PIX')),
    provider            VARCHAR(30) NOT NULL,
    provider_charge_id  VARCHAR(120) NOT NULL,
    amount              NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    due_date            DATE NOT NULL,
    barcode             VARCHAR(60),
    digitable_line      VARCHAR(60),
    pix_copy_paste      TEXT,
    pdf_url             VARCHAR(500),
    status              VARCHAR(15) NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING','PAID','EXPIRED','CANCELLED','FAILED')),
    created_by          UUID REFERENCES users(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (provider, provider_charge_id)
);
CREATE INDEX ix_charges_title ON charges(title_id);

CREATE TABLE payments (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    faculty_id          UUID NOT NULL REFERENCES faculties(id),
    title_id            UUID NOT NULL REFERENCES financial_titles(id),
    charge_id           UUID REFERENCES charges(id),
    provider_event_id   VARCHAR(120),
    amount              NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    interest_amount     NUMERIC(14,2) NOT NULL DEFAULT 0,
    fine_amount         NUMERIC(14,2) NOT NULL DEFAULT 0,
    paid_at             TIMESTAMPTZ NOT NULL,
    method              VARCHAR(15) NOT NULL CHECK (method IN ('BOLETO','PIX','CARTAO','DINHEIRO','TRANSFERENCIA')),
    status              VARCHAR(15) NOT NULL DEFAULT 'CONFIRMED' CHECK (status IN ('CONFIRMED','REFUNDED')),
    source              VARCHAR(15) NOT NULL CHECK (source IN ('WEBHOOK','MANUAL')),
    registered_by       UUID REFERENCES users(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_payments_provider_event ON payments(provider_event_id) WHERE provider_event_id IS NOT NULL;
CREATE INDEX ix_payments_faculty_date ON payments(faculty_id, paid_at);

CREATE TABLE webhook_events (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider          VARCHAR(30) NOT NULL,
    provider_event_id VARCHAR(120) NOT NULL,
    payload           JSONB NOT NULL,
    signature_valid   BOOLEAN NOT NULL,
    processed_at      TIMESTAMPTZ,
    result            VARCHAR(60),
    received_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (provider, provider_event_id)
);

CREATE TABLE accounts_receivable (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    faculty_id    UUID NOT NULL REFERENCES faculties(id),
    title_id      UUID REFERENCES financial_titles(id),
    cost_center_id UUID REFERENCES cost_centers(id),
    description   VARCHAR(200) NOT NULL,
    amount        NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    due_date      DATE NOT NULL,
    received_at   DATE,
    status        VARCHAR(15) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','RECEIVED','CANCELLED')),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_ar_faculty ON accounts_receivable(faculty_id, status, due_date);

CREATE TABLE accounts_payable (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    faculty_id     UUID NOT NULL REFERENCES faculties(id),
    cost_center_id UUID REFERENCES cost_centers(id),
    supplier       VARCHAR(200) NOT NULL,
    description    VARCHAR(200) NOT NULL,
    amount         NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    due_date       DATE NOT NULL,
    paid_at        DATE,
    status         VARCHAR(15) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','PAID','CANCELLED')),
    created_by     UUID REFERENCES users(id),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_ap_faculty ON accounts_payable(faculty_id, status, due_date);

CREATE TABLE financial_simulations (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    faculty_id  UUID NOT NULL REFERENCES faculties(id),
    user_id     UUID NOT NULL REFERENCES users(id),
    calculator  VARCHAR(20) NOT NULL CHECK (calculator IN ('FINANCEIRA','CONTABIL')),
    operation   VARCHAR(40) NOT NULL,
    inputs      JSONB NOT NULL,
    result      JSONB NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
