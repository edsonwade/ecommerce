-- V1_6__add_tenant_id.sql
-- Phase 4: Multi-tenancy — add tenant_id to all order tables
-- Enables per-tenant data isolation via Hibernate @Filter

BEGIN;

-- -------------------------------------------------------
-- customer_order — add tenant_id column
-- -------------------------------------------------------
ALTER TABLE customer_order
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(36);

-- Backfill existing rows with a default tenant (system tenant)
UPDATE customer_order
SET tenant_id = '00000000-0000-0000-0000-000000000000'
WHERE tenant_id IS NULL;

-- Enforce NOT NULL after backfill
ALTER TABLE customer_order
    ALTER COLUMN tenant_id SET NOT NULL;

-- Index for Hibernate filter (WHERE tenant_id = ?)
CREATE INDEX IF NOT EXISTS idx_order_tenant_id
    ON customer_order (tenant_id);

-- -------------------------------------------------------
-- customer_line — add tenant_id column
-- -------------------------------------------------------
ALTER TABLE customer_line
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(36);

UPDATE customer_line
SET tenant_id = '00000000-0000-0000-0000-000000000000'
WHERE tenant_id IS NULL;

ALTER TABLE customer_line
    ALTER COLUMN tenant_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_order_line_tenant_id
    ON customer_line (tenant_id);

-- -------------------------------------------------------
-- outbox_event — add tenant_id for audit trail
-- -------------------------------------------------------
ALTER TABLE outbox_event
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(36);

UPDATE outbox_event
SET tenant_id = '00000000-0000-0000-0000-000000000000'
WHERE tenant_id IS NULL;

ALTER TABLE outbox_event
    ALTER COLUMN tenant_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_outbox_tenant_id
    ON outbox_event (tenant_id);

COMMIT;
