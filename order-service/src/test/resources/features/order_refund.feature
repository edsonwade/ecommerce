Feature: Order refund (Fase 6)
  As the platform
  I want a payment refund to transition the owning order to REFUNDED
  So that stock can be restocked and the customer sees the correct status

  Scenario: A payment.refunded event refunds a confirmed order
    Given a confirmed order exists for the refund
    When a payment.refunded event arrives for that order
    Then the refunded order's status becomes "REFUNDED"
    And an order.refunded outbox event is written

  Scenario: A redelivered payment.refunded event is idempotent
    Given an already-refunded order exists for the refund
    When a payment.refunded event arrives for that order
    Then no new outbox event is written

  Scenario: A payment.refunded event for a non-refundable order is rejected
    Given a requested order exists for the refund
    When a payment.refunded event arrives for that order
    Then the refund is rejected as an illegal transition
