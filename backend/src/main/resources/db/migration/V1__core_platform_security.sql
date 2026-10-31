-- =====================================================================
-- V1 - Núcleo da plataforma: faculdades (tenants), identidade, RBAC,
--      autenticação, TOTP, códigos de recuperação, sessões e auditoria.
-- Convenções:
--   * PK UUID (gen_random_uuid), timestamps em timestamptz (UTC).
--   * Toda tabela de tenant possui faculty_id NOT NULL + índice.
--   * Segredos: senhas = hash Argon2id; TOTP = AES-256-GCM; códigos de
--     recuperação e refresh tokens = SHA-256 (nunca texto puro).
-- =====================================================================

CREATE TABLE plans (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code            VARCHAR(40)  NOT NULL UNIQUE,
    name            VARCHAR(120) NOT NULL,
    max_students    INTEGER      CHECK (max_students IS NULL OR max_students > 0),
    max_users       INTEGER      CHECK (max_users IS NULL OR max_users > 0),
    monthly_price   NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (monthly_price >= 0),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE faculties (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slug                VARCHAR(60)  NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    name                VARCHAR(200) NOT NULL,
    legal_name          VARCHAR(200) NOT NULL,
    trade_name          VARCHAR(200),
    cnpj                VARCHAR(14)     NOT NULL UNIQUE CHECK (cnpj ~ '^[0-9]{14}$'),
    email               VARCHAR(200) NOT NULL,
    phone               VARCHAR(30),
    address_street      VARCHAR(200),
    address_number      VARCHAR(20),
    address_complement  VARCHAR(100),
    address_district    VARCHAR(100),
    address_city        VARCHAR(100),
    address_state       VARCHAR(2),
    address_zip         VARCHAR(9),
    logo_url            VARCHAR(500),
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
                        CHECK (status IN ('ACTIVE','SUSPENDED','ARCHIVED')),
    plan_id             UUID REFERENCES plans(id),
    status_reason       VARCHAR(500),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    archived_at         TIMESTAMPTZ
);
CREATE INDEX ix_faculties_status ON faculties(status);

CREATE TABLE faculty_settings (
    faculty_id                  UUID PRIMARY KEY REFERENCES faculties(id) ON DELETE CASCADE,
    primary_color               VARCHAR(7) NOT NULL DEFAULT '#0B2545',
    academic_min_grade          NUMERIC(4,2) NOT NULL DEFAULT 6.00 CHECK (academic_min_grade BETWEEN 0 AND 10),
    academic_min_attendance_pct NUMERIC(5,2) NOT NULL DEFAULT 75.00 CHECK (academic_min_attendance_pct BETWEEN 0 AND 100),
    late_fee_pct                NUMERIC(5,2) NOT NULL DEFAULT 0 CHECK (late_fee_pct >= 0),
    monthly_interest_pct        NUMERIC(5,2) NOT NULL DEFAULT 0 CHECK (monthly_interest_pct >= 0),
    session_timeout_minutes     INTEGER    NOT NULL DEFAULT 30 CHECK (session_timeout_minutes BETWEEN 5 AND 1440),
    data_retention_years        INTEGER    NOT NULL DEFAULT 20 CHECK (data_retention_years > 0),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------
-- Usuários. faculty_id NULL somente para usuários da plataforma (Master).
-- username é único por faculdade (aluno: username = matrícula).
-- ---------------------------------------------------------------------
CREATE TABLE users (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    faculty_id              UUID REFERENCES faculties(id),
    username                VARCHAR(60)  NOT NULL CHECK (username ~ '^[A-Za-z0-9._-]{3,60}$'),
    full_name               VARCHAR(200) NOT NULL,
    social_name             VARCHAR(200),
    email                   VARCHAR(200) NOT NULL,
    phone                   VARCHAR(30),
    photo_url               VARCHAR(500),
    password_hash           VARCHAR(255) NOT NULL,
    platform_admin          BOOLEAN      NOT NULL DEFAULT FALSE,
    status                  VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
                            CHECK (status IN ('ACTIVE','INACTIVE','BLOCKED')),
    must_change_password    BOOLEAN      NOT NULL DEFAULT FALSE,
    mfa_enabled             BOOLEAN      NOT NULL DEFAULT FALSE,
    failed_login_count      INTEGER      NOT NULL DEFAULT 0,
    locked_until            TIMESTAMPTZ,
    password_changed_at     TIMESTAMPTZ,
    last_login_at           TIMESTAMPTZ,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_users_platform_admin CHECK (
        (platform_admin AND faculty_id IS NULL) OR (NOT platform_admin AND faculty_id IS NOT NULL))
);
-- username único por faculdade; e único entre usuários de plataforma
CREATE UNIQUE INDEX ux_users_faculty_username ON users(faculty_id, lower(username)) WHERE faculty_id IS NOT NULL;
CREATE UNIQUE INDEX ux_users_platform_username ON users(lower(username)) WHERE faculty_id IS NULL;
CREATE INDEX ix_users_faculty ON users(faculty_id);

CREATE TABLE permissions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(80)  NOT NULL UNIQUE,
    module      VARCHAR(40)  NOT NULL,
    description VARCHAR(255) NOT NULL,
    platform_only BOOLEAN    NOT NULL DEFAULT FALSE
);

-- Cargos. faculty_id NULL = modelo do sistema (copiado para cada faculdade).
CREATE TABLE roles (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    faculty_id  UUID REFERENCES faculties(id) ON DELETE CASCADE,
    code        VARCHAR(60)  NOT NULL CHECK (code ~ '^[A-Z0-9_]{2,60}$'),
    name        VARCHAR(120) NOT NULL,
    description VARCHAR(255),
    system_role BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_roles_faculty_code ON roles(faculty_id, code) WHERE faculty_id IS NOT NULL;
CREATE UNIQUE INDEX ux_roles_template_code ON roles(code) WHERE faculty_id IS NULL;

CREATE TABLE role_permissions (
    role_id       UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE user_roles (
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id    UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    granted_by UUID REFERENCES users(id),
    PRIMARY KEY (user_id, role_id)
);
CREATE INDEX ix_user_roles_role ON user_roles(role_id);

-- ---------------------------------------------------------------------
-- TOTP (Google Authenticator). Segredo cifrado AES-256-GCM.
-- status PENDING = QR exibido, ainda não validado; ACTIVE = validado;
-- REVOKED = substituído/desativado. No máximo 1 ACTIVE e 1 PENDING.
-- last_used_step impede reutilização do mesmo código (anti-replay).
-- ---------------------------------------------------------------------
CREATE TABLE user_authenticator_secrets (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    secret_ciphertext TEXT NOT NULL,
    status            VARCHAR(10) NOT NULL CHECK (status IN ('PENDING','ACTIVE','REVOKED')),
    last_used_step    BIGINT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    activated_at      TIMESTAMPTZ,
    revoked_at        TIMESTAMPTZ
);
CREATE UNIQUE INDEX ux_uas_one_active  ON user_authenticator_secrets(user_id) WHERE status = 'ACTIVE';
CREATE UNIQUE INDEX ux_uas_one_pending ON user_authenticator_secrets(user_id) WHERE status = 'PENDING';

CREATE TABLE recovery_codes (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    code_hash   VARCHAR(64) NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_recovery_codes_user ON recovery_codes(user_id) WHERE used_at IS NULL;

-- Desafios de login (etapa entre senha e TOTP) e de step-up (reautenticação).
CREATE TABLE auth_challenges (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    purpose     VARCHAR(30) NOT NULL,
    attempts    INTEGER NOT NULL DEFAULT 0,
    expires_at  TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    ip_address  VARCHAR(64),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_auth_challenges_user ON auth_challenges(user_id);

CREATE TABLE user_sessions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    faculty_id          UUID REFERENCES faculties(id),
    refresh_token_hash  VARCHAR(64) NOT NULL UNIQUE,
    ip_address          VARCHAR(64),
    user_agent          VARCHAR(500),
    device_label        VARCHAR(120),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at          TIMESTAMPTZ NOT NULL,
    revoked_at          TIMESTAMPTZ,
    revoked_reason      VARCHAR(60)
);
CREATE INDEX ix_user_sessions_user_active ON user_sessions(user_id) WHERE revoked_at IS NULL;

CREATE TABLE access_logs (
    id          BIGSERIAL PRIMARY KEY,
    user_id     UUID REFERENCES users(id) ON DELETE SET NULL,
    faculty_id  UUID REFERENCES faculties(id),
    identifier  VARCHAR(120),
    event       VARCHAR(40) NOT NULL,
    success     BOOLEAN NOT NULL,
    ip_address  VARCHAR(64),
    user_agent  VARCHAR(500),
    detail      VARCHAR(255),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_access_logs_user ON access_logs(user_id, created_at DESC);
CREATE INDEX ix_access_logs_faculty ON access_logs(faculty_id, created_at DESC);

-- Auditoria: append-only (UPDATE/DELETE bloqueados por trigger).
CREATE TABLE audit_logs (
    id           BIGSERIAL PRIMARY KEY,
    -- sem FK: a trilha de auditoria sobrevive à exclusão de faculdades/usuários
    faculty_id   UUID,
    actor_id     UUID,
    actor_name   VARCHAR(200),
    action       VARCHAR(80)  NOT NULL,
    entity_type  VARCHAR(60)  NOT NULL,
    entity_id    VARCHAR(80),
    details      JSONB,
    ip_address   VARCHAR(64),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_logs_faculty ON audit_logs(faculty_id, created_at DESC);
CREATE INDEX ix_audit_logs_entity ON audit_logs(entity_type, entity_id);

CREATE FUNCTION fn_audit_logs_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_logs é somente-inserção';
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_audit_logs_immutable BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION fn_audit_logs_immutable();

CREATE TABLE notifications (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    faculty_id  UUID REFERENCES faculties(id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category    VARCHAR(40) NOT NULL,
    title       VARCHAR(200) NOT NULL,
    body        TEXT,
    link        VARCHAR(500),
    read_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_notifications_user ON notifications(user_id, created_at DESC);

CREATE TABLE support_tickets (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    faculty_id  UUID REFERENCES faculties(id) ON DELETE CASCADE,
    opened_by   UUID REFERENCES users(id) ON DELETE SET NULL,
    subject     VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    priority    VARCHAR(10) NOT NULL DEFAULT 'MEDIUM' CHECK (priority IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    status      VARCHAR(15) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','IN_PROGRESS','RESOLVED','CLOSED')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_support_tickets_faculty ON support_tickets(faculty_id, status);
