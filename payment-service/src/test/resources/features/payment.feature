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
