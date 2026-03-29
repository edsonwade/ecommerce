-- V1_4__add_order_saga_fields.sql
-- Phase 3: Add correlationId + status columns for async saga tracking

BEGIN;

ALTER TABLE customer_order
    ADD COLUMN IF NOT EXISTS correlation_id  VARCHAR(36);

ALTER TABLE customer_order
    ADD COLUMN IF NOT EXISTS status          VARCHAR(30) NOT NULL DEFAULT 'REQUESTED';

-- Backfill existing rows with unique correlation IDs
UPDATE customer_order
SET correlation_id = gen_random_uuid()::text
WHERE correlation_id IS NULL;

-- Now enforce NOT NULL
ALTER TABLE customer_order
    ALTER COLUMN correlation_id SET NOT NULL;

-- Unique constraint — each order has one correlation ID
ALTER TABLE customer_order
    ADD CONSTRAINT uq_order_correlation_id UNIQUE (correlation_id);

-- Index for status-polling endpoint (GET /orders/status/{correlationId})
CREATE INDEX IF NOT EXISTS idx_order_correlation_id ON customer_order (correlation_id);

COMMIT;
