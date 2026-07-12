-- V6__add_seller_status.sql
-- Seller approval flow (Fase 2): a SELLER account carries an approval lifecycle
-- (PENDING_APPROVAL / APPROVED / SUSPENDED). Nullable — non-sellers stay NULL.
-- Grandfathering: every SELLER that exists before this migration keeps working
-- exactly as today, so they are marked APPROVED. New self-registered sellers are
-- born PENDING_APPROVAL (application logic); admin-created sellers are APPROVED.

BEGIN;

ALTER TABLE app_user
    ADD COLUMN IF NOT EXISTS seller_status VARCHAR(30);

UPDATE app_user
SET seller_status = 'APPROVED'
WHERE role = 'SELLER'
  AND seller_status IS NULL;

COMMIT;
