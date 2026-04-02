-- =============================================================================
-- V1.3 — Phase 4: Add tenant_id column for multi-tenant isolation
-- =============================================================================
-- Every payment belongs to a tenant. Existing rows get a default placeholder
-- that must be updated during tenant onboarding / data migration.
-- The index accelerates the Hibernate @Filter condition: WHERE tenant_id = :tenantId
-- =============================================================================

-- 1. Add column with a temporary default for existing data
ALTER TABLE payment
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64);

-- 2. Backfill existing rows with a placeholder tenant
UPDATE payment SET tenant_id = 'default-tenant' WHERE tenant_id IS NULL;

-- 3. Apply NOT NULL constraint after backfill
ALTER TABLE payment
    ALTER COLUMN tenant_id SET NOT NULL;

-- 4. Index for Hibernate filter performance (tenant_id appears in every query WHERE clause)
CREATE INDEX IF NOT EXISTS idx_payment_tenant_id ON payment (tenant_id);

-- 5. Composite index: most payment queries filter by tenant + order
CREATE INDEX IF NOT EXISTS idx_payment_tenant_order ON payment (tenant_id, order_id);
