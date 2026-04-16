-- V1_6__add_tenant_id.sql
-- Phase 4: Multi-tenancy — add tenant_id to all order tables
-- Enables per-tenant data isolation via Hibernate @Filter

BEGIN;

-- -------------------------------------------------------
-- customer_line — create table if it was never migrated
-- (OrderLine entity was not present in V1_3; created here
--  so subsequent ALTER TABLE statements can succeed)
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS customer_line (
    id         SERIAL PRIMARY KEY,
    order_id   INTEGER        NOT NULL REFERENCES customer_order (order_id),
    product_id INTEGER,
    quantity   DOUBLE PRECISION,
    tenant_id  VARCHAR(36)    NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000'
);

CREATE INDEX IF NOT EXISTS idx_order_line_order_id
    ON customer_line (order_id);

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
