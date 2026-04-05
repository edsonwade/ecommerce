-- V2__auth_schema_upgrade.sql
-- Production security hardening:
--   1. Replace token_value (full JWT) with jti (UUID) — eliminates token exposure in DB
--   2. Add updated_at audit column to token
--   3. Add CHECK constraint on token_type
--   4. Create app_role table (DB-managed RBAC)
--   5. Create user_roles join table
--   6. Seed default roles and migrate existing users

BEGIN;

-- 1. Add jti column
ALTER TABLE token ADD COLUMN IF NOT EXISTS jti VARCHAR(36);

-- 2. Backfill jti for existing rows using md5(token_value) → UUID format
UPDATE token
SET jti = substring(md5(token_value),1,8)  || '-' ||
          substring(md5(token_value),9,4)  || '-' ||
          '4' || substring(md5(token_value),14,3) || '-' ||
          substring(md5(token_value),17,4) || '-' ||
          substring(md5(token_value),21,12)
WHERE jti IS NULL;

-- 3. Enforce NOT NULL + UNIQUE on jti
ALTER TABLE token ALTER COLUMN jti SET NOT NULL;
ALTER TABLE token ADD CONSTRAINT uq_token_jti UNIQUE (jti);

-- 4. Make token_value nullable (old column kept for zero-downtime; drop in V3 if needed)
ALTER TABLE token ALTER COLUMN token_value DROP NOT NULL;

-- 5. Add updated_at audit column
ALTER TABLE token ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

-- 6. Enforce token_type values at DB level
ALTER TABLE token DROP CONSTRAINT IF EXISTS chk_token_type;
ALTER TABLE token ADD CONSTRAINT chk_token_type
    CHECK (token_type IN ('BEARER', 'REFRESH'));

-- 7. Create RBAC roles table
CREATE TABLE IF NOT EXISTS app_role (
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(50)  NOT NULL UNIQUE,
    description VARCHAR(255),
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 8. Seed built-in roles (matches Role enum)
INSERT INTO app_role (name, description) VALUES
    ('USER',   'Standard customer — place orders, view own data'),
    ('SELLER', 'Seller — manage own product catalog'),
    ('ADMIN',  'Administrator — full platform access')
ON CONFLICT (name) DO NOTHING;

-- 9. Create user_roles join table (many-to-many)
CREATE TABLE IF NOT EXISTS user_roles (
    user_id  BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    role_id  BIGINT NOT NULL REFERENCES app_role(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

-- 10. Backfill user_roles from existing app_user.role column
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM app_user u
JOIN app_role r ON r.name = u.role
ON CONFLICT DO NOTHING;

-- 11. Indexes
CREATE INDEX IF NOT EXISTS idx_token_jti           ON token (jti);
CREATE INDEX IF NOT EXISTS idx_token_active_type   ON token (user_id, expired, revoked, token_type);
CREATE INDEX IF NOT EXISTS idx_user_roles_user     ON user_roles (user_id);
DROP INDEX  IF EXISTS idx_token_value;

COMMIT;
