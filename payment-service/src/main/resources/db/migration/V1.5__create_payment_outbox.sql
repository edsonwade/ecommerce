-- V1.5__create_payment_outbox.sql
-- Transactional Outbox Pattern for payment-service (Fase 6.1 — refund latency fix).
-- The payment status change (REFUNDED) and the payment.refunded event row are written
-- in the SAME transaction, so the HTTP refund request no longer blocks on a Kafka send.
-- PaymentOutboxPublisher (scheduled) drains PENDING rows to Kafka off the request thread.
-- Additive: does NOT touch the existing `payment` table. Never H2 — real PostgreSQL only.

BEGIN;

CREATE TABLE IF NOT EXISTS payment_outbox_event (
    id              VARCHAR(36)  PRIMARY KEY DEFAULT gen_random_uuid()::text,
    tenant_id       VARCHAR(36)  NOT NULL,
    event_id        VARCHAR(36)  NOT NULL UNIQUE,
    correlation_id  VARCHAR(36)  NOT NULL,
    topic           VARCHAR(100) NOT NULL,
    payload         TEXT         NOT NULL,
    partition_key   VARCHAR(36),
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    retry_count     INT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at    TIMESTAMP
);

-- Index for the PaymentOutboxPublisher scheduler fetch (status + FIFO by created_at).
CREATE INDEX IF NOT EXISTS idx_payment_outbox_status_created
    ON payment_outbox_event (status, created_at)
    WHERE status = 'PENDING';

-- Index for the idempotency guard (existsByEventId).
CREATE INDEX IF NOT EXISTS idx_payment_outbox_event_id
    ON payment_outbox_event (event_id);

COMMIT;
