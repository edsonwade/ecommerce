-- V1__create_auth_tables.sql
-- Authentication service schema
-- Users, roles, and JWT token blacklist

BEGIN;

CREATE TABLE IF NOT EXISTS app_user (
    id                  BIGSERIAL PRIMARY KEY,
    firstname           VARCHAR(100)  NOT NULL,
    lastname            VARCHAR(100)  NOT NULL,
    email               VARCHAR(255)  NOT NULL UNIQUE,
    password            VARCHAR(255)  NOT NULL,
    role                VARCHAR(50)   NOT NULL DEFAULT 'USER',
    tenant_id           VARCHAR(100)  NOT NULL DEFAULT 'default',
    account_locked      BOOLEAN       NOT NULL DEFAULT FALSE,
    account_enabled     BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP
);

-- Index for fast lookup by email (login flow)
CREATE INDEX IF NOT EXISTS idx_user_email    ON app_user (email);
CREATE INDEX IF NOT EXISTS idx_user_tenant   ON app_user (tenant_id);

CREATE TABLE IF NOT EXISTS token (
    id              BIGSERIAL PRIMARY KEY,
    token_value     TEXT          NOT NULL UNIQUE,
    token_type      VARCHAR(20)   NOT NULL DEFAULT 'BEARER',
    expired         BOOLEAN       NOT NULL DEFAULT FALSE,
    revoked         BOOLEAN       NOT NULL DEFAULT FALSE,
    user_id         BIGINT        NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_token_user    ON token (user_id);
CREATE INDEX IF NOT EXISTS idx_token_value   ON token (token_value);
CREATE INDEX IF NOT EXISTS idx_token_active  ON token (user_id, expired, revoked);

COMMIT;
