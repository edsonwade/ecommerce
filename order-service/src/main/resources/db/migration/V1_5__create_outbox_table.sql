-- V1_5__create_outbox_table.sql
-- Transactional Outbox Pattern: guarantees at-least-once Kafka delivery
-- Order + OutboxEvent written in the SAME transaction → zero dual-write inconsistency

BEGIN;

CREATE TABLE IF NOT EXISTS outbox_event (
    id              VARCHAR(36)  PRIMARY KEY DEFAULT gen_random_uuid()::text,
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

-- Index for the OutboxEventPublisher scheduler query
CREATE INDEX IF NOT EXISTS idx_outbox_status_retry
    ON outbox_event (status, retry_count)
    WHERE status = 'PENDING';

-- Index for idempotency guard
CREATE INDEX IF NOT EXISTS idx_outbox_event_id ON outbox_event (event_id);

COMMIT;
