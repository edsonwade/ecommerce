-- ============================================================
-- F7 — Product reviews (Task 7.2)
-- One review per (product, customer). tenant_id is stamped on the row for the
-- Hibernate @Filter (B3) but is intentionally OUT of the unique key (T1): product_id
-- belongs to exactly one tenant, so (product_id, customer_id) already fixes the tenant —
-- putting tenant_id in the key would be redundant and could weaken the duplicate guard.
-- Additive only; no existing table/column is touched.
-- ============================================================

CREATE TABLE IF NOT EXISTS product_review (
    id          BIGSERIAL     PRIMARY KEY,
    product_id  INTEGER       NOT NULL REFERENCES product (id),
    customer_id BIGINT        NOT NULL,
    rating      INTEGER       NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment     VARCHAR(2000),
    tenant_id   VARCHAR(50)   NOT NULL,
    created_at  TIMESTAMP     NOT NULL DEFAULT now(),
    CONSTRAINT uq_review_customer_product UNIQUE (product_id, customer_id)
);

CREATE INDEX IF NOT EXISTS idx_review_product ON product_review (product_id);
