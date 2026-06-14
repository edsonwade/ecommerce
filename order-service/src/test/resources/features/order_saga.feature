@saga
Feature: Order Saga — Kafka Event Processing (Phase 0)
  As an order-service
  I want to process saga outcome events from Kafka
  So that order status transitions correctly and confirmation notifications are sent

  Background:
    Given a correlation ID "corr-saga-bdd-001" is tracked

  @saga
  Scenario: Payment authorized — order confirmed and notification sent
    When a payment.authorized event is received for "corr-saga-bdd-001"
    Then the order status is updated to CONFIRMED
    And an OrderConfirmation notification is sent via OrderProducer
    And the Kafka offset is acknowledged

  @saga
  Scenario: Payment failed — order cancelled, no notification sent
    When a payment.failed event is received for "corr-saga-bdd-001"
    Then the order status is updated to CANCELLED
    And no OrderConfirmation notification is sent
    And the Kafka offset is acknowledged

  @saga
  Scenario: Inventory insufficient — order cancelled in a single terminal update
    When an inventory.insufficient event is received for "corr-saga-bdd-001"
    Then the order status is updated to CANCELLED
    And the Kafka offset is acknowledged

  @saga
  Scenario: Order confirmation notification failure does not block order confirmation
    Given the OrderProducer throws an exception on sendOrderConfirmation
    When a payment.authorized event is received for "corr-saga-bdd-001"
    Then the order status is updated to CONFIRMED
    And the Kafka offset is acknowledged
