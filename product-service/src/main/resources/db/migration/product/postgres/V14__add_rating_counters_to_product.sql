-- =====================================================================
-- V14 — Fase 7 Task 7.3 (Marketplace Role Capabilities): catalog ratings.
-- Denormalised review counters on product (Decision A1): the catalogue
-- reads stars straight off the product row at zero query cost, instead of
-- aggregating product_review on every list/search/detail read.
-- NOT NULL DEFAULT 0 grandfathers every existing row, so a product with no
-- reviews reads as 0 / 0 (never null) and the entity mirrors the same
-- defaults in Java. Kept in sync by the recompute-from-source UPDATE that
-- ReviewService runs in the same transaction as each review write/delete.
-- =====================================================================
ALTER TABLE product ADD COLUMN IF NOT EXISTS average_rating NUMERIC(2, 1) NOT NULL DEFAULT 0;
ALTER TABLE product ADD COLUMN IF NOT EXISTS review_count   INT           NOT NULL DEFAULT 0;

-- Backfill from reviews already written by Task 7.2 (table created in V13).
-- Products with no reviews are left untouched by the join and keep the 0 / 0 default.
UPDATE product p
SET review_count   = agg.cnt,
    average_rating = agg.avg
FROM (SELECT product_id,
             COUNT(*)              AS cnt,
             ROUND(AVG(rating), 1) AS avg
      FROM product_review
      GROUP BY product_id) agg
WHERE p.id = agg.product_id;
