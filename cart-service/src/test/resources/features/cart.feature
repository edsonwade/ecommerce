Feature: Cart Management
  As a customer
  I want to manage items in my shopping cart
  So that I can eventually purchase them

  Scenario: Add an item to a new cart
    Given the cart for customer "cust-100" is empty
    When I add product 1 with quantity 2.0 to the cart
    Then the cart should contain 1 item with product ID 1
    And the total quantity for product 1 should be 2.0

  Scenario: Add the same item twice merges quantity
    Given the cart for customer "cust-ADD" has product 1 with quantity 2.0
    When I add product 1 with quantity 3.0 to the cart
    Then the cart should contain 1 item with product ID 1
    And the total quantity for product 1 should be 5.0

  Scenario: Add an item with invalid quantity fails
    Given the cart for customer "cust-100" is empty
    When I attempt to add product 1 with quantity -5.0 to the cart
    Then the system should throw a validation error

  Scenario: Remove an item from the cart
    Given the cart for customer "cust-REM" has product 1 with quantity 2.0
    When I remove product 1 from the cart
    Then the cart should be empty

  Scenario: Checkout an empty cart fails
    Given the cart for customer "cust-EMPTY" is empty
    When I checkout the cart
    Then the system should throw a validation error
