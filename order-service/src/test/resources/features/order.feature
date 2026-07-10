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

  Scenario: Create order without reference and receive auto-generated reference
    Given a valid customer with ID "cust-001" exists
    And a valid order request without a reference
    When the order is submitted
    Then the system returns a correlationId

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

  # ── Seller data-isolation: a seller must see ONLY the lines for their own products,
  #    never other sellers' lines nor the platform/"system"-owned catalog lines. ──
  Scenario: Seller sees only their own lines in a multi-seller order
    Given order 500 owned by customer "cust-9" has the lines:
      | productId | sellerId |
      | 10        | 55       |
      | 20        | 88       |
      | 30        | system   |
    When the seller "55" requests the lines of order 500
    Then exactly 1 order line is returned
    And the returned order line is for product 10

  Scenario: Seller who sold nothing in the order cannot view it
    Given order 500 owned by customer "cust-9" has the lines:
      | productId | sellerId |
      | 10        | 55       |
      | 20        | 88       |
    When the seller "77" requests the lines of order 500
    Then the order line request is forbidden

  Scenario: Customer-owner sees every line in their own order
    Given order 500 owned by customer "9" has the lines:
      | productId | sellerId |
      | 10        | 55       |
      | 20        | 88       |
    When the customer "9" requests the lines of order 500
    Then exactly 2 order lines are returned

  Scenario: Admin sees every line in any order
    Given order 500 owned by customer "cust-9" has the lines:
      | productId | sellerId |
      | 10        | 55       |
      | 20        | 88       |
    When an admin requests the lines of order 500
    Then exactly 2 order lines are returned

  # ── Tenant isolation (B3 Fase 1b): a by-id order read is scoped to the caller's tenant.
  #    The caller is bound to tenant "test-tenant" for these scenarios. ──
  Scenario: A tenant reads its own order by id
    Given an order 700 tagged with tenant "test-tenant" exists
    When order 700 is requested by id
    Then the order by id is returned

  Scenario: A tenant cannot read another tenant's order by id
    Given an order 700 tagged with tenant "other-tenant" exists
    When order 700 is requested by id
    Then the order by id is not found
