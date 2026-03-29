-- V1.2 — Add idempotency_key and order_reference columns to payment table
-- This migration adds the critical idempotency guard:
-- unique constraint on idempotency_key ensures a single charge per order reference
-- even under retries, network failures, or duplicate Feign calls.

BEGIN;

-- Add order_reference column (required for idempotency key derivation)
ALTER TABLE payment
    ADD COLUMN IF NOT EXISTS order_reference VARCHAR(255);

-- Add idempotency_key column with a UNIQUE constraint
-- Format: "payment:{orderReference}"
-- DB-level uniqueness is the final safety net against double charges.
ALTER TABLE payment
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(255);

ALTER TABLE payment
    ADD CONSTRAINT uq_payment_idempotency_key UNIQUE (idempotency_key);

-- Backfill existing rows with a derived idempotency key so the NOT NULL
-- constraint can be added safely after the migration runs on existing data.
UPDATE payment
SET idempotency_key = 'payment:legacy-' || payment_id::text,
    order_reference  = 'legacy-' || payment_id::text
WHERE idempotency_key IS NULL;

-- Now enforce NOT NULL
ALTER TABLE payment
    ALTER COLUMN idempotency_key SET NOT NULL;

ALTER TABLE payment
    ALTER COLUMN order_reference SET NOT NULL;

COMMIT;
