Feature: Internal purchase verification (F7)
  Before product-service accepts a product review, it asks order-service whether the
  customer actually bought the product. Only fulfilled orders (CONFIRMED / SHIPPED /
  DELIVERED) count as a purchase — an unpaid or still-processing order does not.

  Scenario: A customer with a fulfilled order is a verified buyer
    Given customer "42" has a "CONFIRMED" order line for product 1
    When product-service checks whether customer "42" purchased product 1
    Then the purchase verification result is true

  Scenario: A customer who never ordered the product is not a verified buyer
    Given customer "42" has a "CONFIRMED" order line for product 1
    When product-service checks whether customer "42" purchased product 999
    Then the purchase verification result is false

  Scenario: A customer whose order is not yet fulfilled is not a verified buyer
    Given customer "55" has a "REQUESTED" order line for product 2
    When product-service checks whether customer "55" purchased product 2
    Then the purchase verification result is false

  Scenario: A shipped order also counts as a fulfilled purchase
    Given customer "7" has a "SHIPPED" order line for product 3
    When product-service checks whether customer "7" purchased product 3
    Then the purchase verification result is true
