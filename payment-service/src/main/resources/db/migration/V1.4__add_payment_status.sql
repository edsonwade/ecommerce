-- V1.4__add_payment_status.sql
-- Fase 6 (basic refunds). Every payment now carries a status: AUTHORIZED (default,
-- grandfathers every existing row) or REFUNDED (terminal, set once by
-- PaymentService.refundPayment). A second refund attempt is rejected with 409 before
-- any write, so this column never needs a CHECK constraint on its own.

ALTER TABLE payment ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'AUTHORIZED';
