Feature: Inventory reservation idempotency
  As the order saga
  I want redelivered order.requested events to reserve stock only once
  So that duplicate Kafka deliveries never double-deduct inventory

  Scenario: A redelivered reservation event does not deduct stock twice
    Given a reservation catalog with product 1 named "Laptop" stocked at 10
    When an order requested event for product 1 with quantity 3 is delivered
    And the same order requested event is delivered again
    Then the reserved stock for product 1 should be 7
    And only one reservation record should exist for the order
    And the inventory reserved event should have been published 2 times

  Scenario: A duplicate delivery after saga compensation is ignored
    Given a reservation catalog with product 1 named "Laptop" stocked at 10
    And the order was already compensated with a released reservation of 3 units of product 1
    When an order requested event for product 1 with quantity 3 is delivered
    Then the reserved stock for product 1 should be 10
    And the inventory reserved event should have been published 0 times

  Scenario: First delivery reserves stock and records the reservation
    Given a reservation catalog with product 1 named "Laptop" stocked at 10
    When an order requested event for product 1 with quantity 4 is delivered
    Then the reserved stock for product 1 should be 6
    And only one reservation record should exist for the order
    And the inventory reserved event should have been published 1 times
