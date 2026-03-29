# ============================================================
# Feature: Product Purchase (Stock Reservation)
# BDD Cucumber feature file — describes the purchase flow in
# business-readable language (Given/When/Then).
# ============================================================

Feature: Product stock reservation during order purchase
  As an order-service
  I want to reserve product stock atomically
  So that no two concurrent orders can both consume the last item

  Background:
    Given the product catalog contains the following products:
      | id | name        | availableQuantity | price   |
      | 1  | Laptop      | 10                | 1500.00 |
      | 2  | Headphones  | 5                 | 250.00  |
      | 3  | Webcam      | 0                 | 80.00   |

  Scenario: Successful purchase with sufficient stock
    When I request to purchase the following products:
      | productId | quantity |
      | 1         | 2        |
      | 2         | 1        |
    Then the purchase should succeed
    And the available quantity for product 1 should be 8
    And the available quantity for product 2 should be 4

  Scenario: Purchase fails when stock is insufficient
    When I request to purchase the following products:
      | productId | quantity |
      | 1         | 20       |
    Then the purchase should fail with a stock error
    And the available quantity for product 1 should remain 10

  Scenario: Purchase fails when product does not exist
    When I request to purchase the following products:
      | productId | quantity |
      | 999       | 1        |
    Then the purchase should fail with a not found error

  Scenario: Purchase fails when requesting out-of-stock product
    When I request to purchase the following products:
      | productId | quantity |
      | 3         | 1        |
    Then the purchase should fail with a stock error
    And the available quantity for product 3 should remain 0

  Scenario: Purchase with empty request list is rejected
    When I request to purchase an empty list of products
    Then the purchase should fail with a validation error
