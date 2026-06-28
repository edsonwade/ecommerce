-- V5__add_password_reset_token.sql
-- Self-service password reset (forgot-password flow) for all roles. A reset request stores
-- only the SHA-256 HASH of a single-use, time-boxed token — the raw token travels only in the
-- emailed link, so a DB leak cannot be replayed. used_at flips the token to single-use; a NULL
-- expires_at never occurs (always set on insert). Rows are short-lived and safe to prune later.

BEGIN;

CREATE TABLE IF NOT EXISTS password_reset_token (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64)  NOT NULL UNIQUE,        -- hex SHA-256 of the raw token
    expires_at  TIMESTAMP    NOT NULL,
    used_at     TIMESTAMP,                           -- NULL until consumed (single-use)
    created_at  TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_password_reset_token_user ON password_reset_token (user_id);

COMMIT;
