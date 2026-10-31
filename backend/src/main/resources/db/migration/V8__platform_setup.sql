-- =====================================================================
-- V8 - Instalação inicial da plataforma (primeiro administrador Master).
-- Linha única. O código de instalação é exibido apenas no console do servidor;
-- somente hashes e o segredo TOTP cifrado são persistidos.
-- =====================================================================
CREATE TABLE platform_setup (
    id                 SMALLINT PRIMARY KEY CHECK (id = 1),
    code_hash          VARCHAR(64) NOT NULL,
    code_expires_at    TIMESTAMPTZ NOT NULL,
    attempts           INTEGER NOT NULL DEFAULT 0,
    token_hash         VARCHAR(64),
    token_expires_at   TIMESTAMPTZ,
    secret_ciphertext  TEXT,
    totp_verified      BOOLEAN NOT NULL DEFAULT FALSE,
    last_used_step     BIGINT,
    completed_at       TIMESTAMPTZ,
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
