Feature: Refund restock (Fase 6)
  As the platform
  I want a refunded order's reserved stock restored
  So that other customers can purchase it again

  Scenario: Restocking a reserved product on order.refunded
    Given a reserved product with quantity 3 for the refund correlation
    When the order.refunded event is consumed
    Then the product stock is restored by 3
    And the reservation is marked RELEASED

  Scenario: Redelivering order.refunded is idempotent
    Given no RESERVED records exist for the refund correlation
    When the order.refunded event is consumed
    Then the product stock is not touched
