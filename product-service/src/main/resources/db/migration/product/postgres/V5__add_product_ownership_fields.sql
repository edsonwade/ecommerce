-- V5 — Phase 4 (RBAC): ownership audit on Product.
-- createdBy = userId (from JWT claims) of the SELLER/ADMIN who created the row.
-- updatedBy = userId of the last user to mutate the product.
ALTER TABLE product
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255);

UPDATE product SET created_by = 'system' WHERE created_by IS NULL;

ALTER TABLE product
    ALTER COLUMN created_by SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_product_created_by ON product (created_by);
