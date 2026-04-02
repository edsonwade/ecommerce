-- =============================================================================
-- V4 — Phase 4: Add tenant_id column for multi-tenant isolation
-- =============================================================================
-- Every product belongs to a tenant. Existing rows get a default placeholder
-- that must be updated during tenant onboarding / data migration.
-- The index accelerates the Hibernate @Filter condition: WHERE tenant_id = :tenantId
-- =============================================================================

-- 1. Add column with a temporary default for existing data
ALTER TABLE product
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64);

-- 2. Backfill existing rows with a placeholder tenant
UPDATE product SET tenant_id = 'default-tenant' WHERE tenant_id IS NULL;

-- 3. Apply NOT NULL constraint after backfill
ALTER TABLE product
    ALTER COLUMN tenant_id SET NOT NULL;

-- 4. Index for Hibernate filter performance (tenant_id appears in every query WHERE clause)
CREATE INDEX IF NOT EXISTS idx_product_tenant_id ON product (tenant_id);

-- 5. Add tenant_id to category as well for consistent isolation
ALTER TABLE category
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64);

UPDATE category SET tenant_id = 'default-tenant' WHERE tenant_id IS NULL;

ALTER TABLE category
    ALTER COLUMN tenant_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_category_tenant_id ON category (tenant_id);
