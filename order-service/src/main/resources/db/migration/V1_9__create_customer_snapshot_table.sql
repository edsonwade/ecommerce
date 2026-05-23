-- Phase 2: CQRS read model for customer data
-- Populated by order-service's CustomerEventConsumer (consumer of customer.profile Kafka topic).
-- Allows OrderService.resolveCustomer() to avoid a synchronous Feign call to customer-service
-- on 99%+ of order creations once snapshot coverage is established.

CREATE TABLE IF NOT EXISTS customer_snapshot (
    customer_id  VARCHAR(64)  NOT NULL,
    firstname    VARCHAR(128),
    lastname     VARCHAR(128),
    email        VARCHAR(256),
    tenant_id    VARCHAR(64),
    last_updated TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_customer_snapshot PRIMARY KEY (customer_id)
);

CREATE INDEX IF NOT EXISTS idx_customer_snapshot_email
    ON customer_snapshot (email);

CREATE INDEX IF NOT EXISTS idx_customer_snapshot_tenant
    ON customer_snapshot (tenant_id);
