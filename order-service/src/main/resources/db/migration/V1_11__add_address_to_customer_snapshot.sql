-- V1_11__add_address_to_customer_snapshot.sql
-- Invoice-grade order detail: the seller fulfilling an order (and the buyer reviewing it)
-- needs the shipping address. customer-service already owns Customer.address; we carry it
-- into the local CQRS read model so order-service can serve it WITHOUT a cross-user Feign
-- call (the customer endpoint is ADMIN-or-self — a seller could never fetch a buyer's
-- profile over HTTP). Columns are populated by CustomerEventConsumer from the enriched
-- customer.profile event. Nullable: existing snapshots and customers without an address
-- on file simply surface as "not provided".

BEGIN;

ALTER TABLE customer_snapshot
    ADD COLUMN IF NOT EXISTS street       VARCHAR(256),
    ADD COLUMN IF NOT EXISTS house_number VARCHAR(64),
    ADD COLUMN IF NOT EXISTS zip_code     VARCHAR(64),
    ADD COLUMN IF NOT EXISTS city         VARCHAR(128),
    ADD COLUMN IF NOT EXISTS country      VARCHAR(128);

COMMIT;
