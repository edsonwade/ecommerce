-- V1_10__add_seller_id_to_order_line.sql
-- Marketplace: stamp the owning seller (product.created_by) on every order line so a
-- seller can see exactly the orders placed for THEIR products — competing sellers are
-- isolated by ownership, not by the shared "default" tenant.
-- Nullable: pre-existing lines (and any line whose product lookup fails at creation)
-- stay NULL and simply won't surface to a seller.

BEGIN;

ALTER TABLE customer_line
    ADD COLUMN IF NOT EXISTS seller_id VARCHAR(255);

-- Index for the seller order query (WHERE seller_id = ?)
CREATE INDEX IF NOT EXISTS idx_order_line_seller_id
    ON customer_line (seller_id);

COMMIT;
