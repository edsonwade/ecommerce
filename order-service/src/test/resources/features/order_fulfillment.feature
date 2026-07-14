Feature: Order fulfillment status (Fase 5)
  As a seller or admin
  I want to advance an order through SHIPPED and DELIVERED
  So that customers can track fulfillment after the saga confirms the order

  Background:
    Given a confirmed order owned by seller "7"

  Scenario: Admin ships a confirmed order
    Given the current actor is "ADMIN" with id 1
    When the actor sets the order status to "SHIPPED"
    Then the order status becomes "SHIPPED"
    And the shipped timestamp is recorded

  Scenario: Seller who owns a line ships the order
    Given the current actor is "SELLER" with id 7
    When the actor sets the order status to "SHIPPED"
    Then the order status becomes "SHIPPED"

  Scenario: Seller without a line in the order is forbidden
    Given the current actor is "SELLER" with id 99
    When the actor sets the order status to "SHIPPED"
    Then the update is forbidden

  Scenario: A status outside the fulfillment whitelist is rejected
    Given the current actor is "ADMIN" with id 1
    When the actor sets the order status to "CONFIRMED"
    Then the update is rejected as not allowed

  Scenario: Delivering before shipping is an illegal transition
    Given the current actor is "ADMIN" with id 1
    When the actor sets the order status to "DELIVERED"
    Then the update is rejected as an illegal transition
