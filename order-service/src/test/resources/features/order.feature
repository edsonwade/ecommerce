Feature: Async Order Creation and Status Polling
  As a client application
  I want to create orders asynchronously
  So that my requests are accepted immediately and processed via saga

  Scenario: Successfully create an order and receive correlationId
    Given a valid customer with ID "cust-001" exists
    And a valid order request with reference "ORD-BDD-001" and amount 299.99
    When the order is submitted
    Then the system returns a correlationId
    And the order status is "REQUESTED"

  Scenario: Fail to create order when customer does not exist
    Given no customer with ID "ghost-cust" exists
    And a valid order request for customer "ghost-cust"
    When the order is submitted
    Then the system rejects the order with a customer unavailable error

  Scenario: Poll order status after creation
    Given an order with correlationId "corr-poll-001" exists in REQUESTED status
    When the order status is polled with correlationId "corr-poll-001"
    Then the returned status is "REQUESTED"

  Scenario: Order status transitions to CONFIRMED after saga completes
    Given an order with correlationId "corr-confirm-001" exists in CONFIRMED status
    When the order status is polled with correlationId "corr-confirm-001"
    Then the returned status is "CONFIRMED"

  Scenario: Order status transitions to CANCELLED on saga compensation
    Given an order with correlationId "corr-cancel-001" exists in CANCELLED status
    When the order status is polled with correlationId "corr-cancel-001"
    Then the returned status is "CANCELLED"

  Scenario: Fail to poll status for unknown correlationId
    Given no order with correlationId "unknown-id" exists
    When the order status is polled with correlationId "unknown-id"
    Then the system returns an order not found error

  Scenario: List all orders
    Given the following orders exist:
      | orderId | reference   | amount | customerId |
      | 1       | ORD-LIST-01 | 100.00 | cust-001   |
      | 2       | ORD-LIST-02 | 250.00 | cust-002   |
    When all orders are requested
    Then the system returns 2 orders
