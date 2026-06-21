-- V1_12__add_invoice_fields_to_order.sql
-- Real e-commerce invoice: an order needs a money breakdown, not just one total.
-- We add the persisted fields so the schema is invoice-ready (discount/promotion can be
-- captured by checkout later). All nullable — for existing orders the breakdown is derived
-- on read from total_amount + the configured IVA rate (tax-inclusive pricing), and
-- discount/promotion are truthfully absent (none was applied).
--
--   subtotal         = net amount (total / (1 + tax_rate))
--   tax_amount       = IVA portion (total - subtotal)
--   tax_rate         = IVA rate applied (e.g. 0.23); null => use config default on read
--   discount_amount  = order-level discount (0 until checkout captures one)
--   promotion_code   = applied promo code (null until a promotion engine exists)
--   promotion_amount = value discounted by the promotion (0 until then)
-- total_amount (existing, unchanged) remains the authoritative amount the customer paid.

BEGIN;

ALTER TABLE customer_order
    ADD COLUMN IF NOT EXISTS subtotal         NUMERIC(19, 2),
    ADD COLUMN IF NOT EXISTS discount_amount  NUMERIC(19, 2),
    ADD COLUMN IF NOT EXISTS promotion_code   VARCHAR(64),
    ADD COLUMN IF NOT EXISTS promotion_amount NUMERIC(19, 2),
    ADD COLUMN IF NOT EXISTS tax_rate         NUMERIC(6, 4),
    ADD COLUMN IF NOT EXISTS tax_amount       NUMERIC(19, 2);

COMMIT;
