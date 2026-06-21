-- V4__add_seller_business_profile.sql
-- Invoice-grade order detail needs the seller's legal/business identity (the "sold by"
-- block on a real invoice). Sellers are app_user rows; we add their business profile as
-- nullable columns so existing users are unaffected and a seller fills these in from the
-- Business Profile settings form. Surfaced via GET /api/v1/auth/sellers/{id}.

BEGIN;

ALTER TABLE app_user
    ADD COLUMN IF NOT EXISTS company_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS vat_number   VARCHAR(64),
    ADD COLUMN IF NOT EXISTS street       VARCHAR(256),
    ADD COLUMN IF NOT EXISTS city         VARCHAR(128),
    ADD COLUMN IF NOT EXISTS country      VARCHAR(128),
    ADD COLUMN IF NOT EXISTS postal_code  VARCHAR(64);

COMMIT;
