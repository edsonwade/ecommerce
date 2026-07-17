Feature: Process Payments
  As an order microservice
  I need to process payments idempotently
  So that customers are not charged twice

  Scenario: Successfully process a new payment
    Given a valid payment request for order "ORD-PAY-1" with amount 100.00
    And the payment has not been processed yet
    When the payment is submitted
    Then the payment should be recorded with status "SUCCESS"
    And a notification should be sent for order "ORD-PAY-1"

  Scenario: Reject duplicate payment process (Idempotency)
    Given a valid payment request for order "ORD-PAY-DUP" with amount 100.00
    And the payment has already been processed successfully
    When the payment is submitted
    Then the system should reject the duplicate payment request

  Scenario: Refund an authorized payment
    Given an authorized payment exists for order "ORD-PAY-REFUND"
    When the payment is refunded
    Then the payment status becomes "REFUNDED"
    And a payment.refunded event is published

  Scenario: Reject refunding an already-refunded payment
    Given an already-refunded payment exists for order "ORD-PAY-REREFUND"
    When the payment is refunded
    Then the refund is rejected as already processed
