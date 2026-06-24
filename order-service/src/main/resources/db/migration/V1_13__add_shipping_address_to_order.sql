-- V1_13__add_shipping_address_to_order.sql
-- Per-order shipping address. Until now the shipping address shown on an invoice was read
-- from the customer's PROFILE snapshot (customer_snapshot) — so the address typed at
-- checkout was discarded and orders showed "Shipping address not provided" whenever the
-- profile had none. Real e-commerce captures the destination ON the order itself: each
-- order keeps the address the buyer entered at checkout, independent of their profile.
--
-- All nullable: legacy orders have no per-order address and fall back to the customer
-- snapshot on read (OrderMapper.fromOrder).

BEGIN;

ALTER TABLE customer_order
    ADD COLUMN IF NOT EXISTS shipping_street       VARCHAR(256),
    ADD COLUMN IF NOT EXISTS shipping_house_number VARCHAR(64),
    ADD COLUMN IF NOT EXISTS shipping_zip_code     VARCHAR(64),
    ADD COLUMN IF NOT EXISTS shipping_city         VARCHAR(128),
    ADD COLUMN IF NOT EXISTS shipping_country      VARCHAR(128);

COMMIT;
