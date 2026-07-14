-- V1_14__add_fulfillment_timestamps.sql
-- Fase 5 (order fulfillment). OrderStatus gains SHIPPED / DELIVERED / REFUNDED and CONFIRMED
-- stops being terminal. When a seller/admin advances a confirmed order we record WHEN it was
-- shipped and delivered, so the invoice/tracking view can show the fulfillment timeline.
--
-- Both nullable: legacy orders and any order not yet shipped have no timestamp. The status
-- column itself is unchanged (already VARCHAR(30), wide enough for the new enum names).

BEGIN;

ALTER TABLE customer_order
    ADD COLUMN IF NOT EXISTS shipped_at   TIMESTAMP,
    ADD COLUMN IF NOT EXISTS delivered_at TIMESTAMP;

COMMIT;
