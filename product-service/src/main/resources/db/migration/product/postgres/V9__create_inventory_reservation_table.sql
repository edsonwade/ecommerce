CREATE TABLE inventory_reservation (
    id                BIGSERIAL PRIMARY KEY,
    correlation_id    VARCHAR(255) NOT NULL,
    product_id        INTEGER      NOT NULL,
    reserved_quantity INTEGER      NOT NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'RESERVED',
    created_at        TIMESTAMP,
    released_at       TIMESTAMP
);

CREATE INDEX idx_inventory_reservation_correlation_status
    ON inventory_reservation (correlation_id, status);
