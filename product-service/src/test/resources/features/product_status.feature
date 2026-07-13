Feature: Product lifecycle status (Fase 3 — Task 3.1)
  Every product carries a lifecycle status. New products are born ACTIVE;
  the status round-trips into the product detail response, so admins (and
  later the public read filter of Task 3.2) can rely on it.

  Scenario: a newly created product is born ACTIVE
    Given an approved seller is signed in for the status feature
    When the seller submits a status-feature product named "Fresh-Widget"
    Then the product saved by the status feature has status "ACTIVE"

  Scenario: the product detail response carries an explicit SUSPENDED status
    Given an approved seller is signed in for the status feature
    And the status feature has a stored product 5 named "Paused-Gadget" with status "SUSPENDED" owned by the seller
    When the status feature requests the detail of product 5
    Then the status feature detail response has status "SUSPENDED"

  Scenario: a suspended product is invisible to a non-owner (Task 3.2)
    Given an approved seller is signed in for the status feature
    And the status feature has a stored product 9 named "Ghost-Gadget" with status "SUSPENDED" owned by another seller
    When the status feature requests the detail of product 9 expecting a failure
    Then the status feature detail request fails as not found

  Scenario: an admin still sees a suspended product's detail (Task 3.2)
    Given an admin is signed in for the status feature
    And the status feature has a stored product 9 named "Ghost-Gadget" with status "SUSPENDED" owned by another seller
    When the status feature requests the detail of product 9
    Then the status feature detail response has status "SUSPENDED"

  Scenario: a suspended product cannot be purchased (Task 3.3)
    Given an approved seller is signed in for the status feature
    And the status feature has a purchasable product 4 named "Frozen-Widget" with status "SUSPENDED" and stock 10
    When the status feature purchases 2 units of product 4
    Then the status feature purchase is rejected with reason "product.suspended"
    And the status feature deducted no stock

  Scenario: an active product with stock purchases normally (Task 3.3 regression)
    Given an approved seller is signed in for the status feature
    And the status feature has a purchasable product 4 named "Live-Widget" with status "ACTIVE" and stock 10
    When the status feature purchases 2 units of product 4
    Then the status feature purchase succeeds

  Scenario: an admin suspends a product (Task 3.4)
    Given an admin is signed in for the status feature
    And the status feature has a stored product 5 named "Target-Widget" with status "ACTIVE" owned by another seller
    When the admin sets the status of product 5 to "SUSPENDED" in the status feature
    Then the status feature saved status change is "SUSPENDED"

  Scenario: an admin reactivates a suspended product (Task 3.4)
    Given an admin is signed in for the status feature
    And the status feature has a stored product 5 named "Target-Widget" with status "SUSPENDED" owned by another seller
    When the admin sets the status of product 5 to "ACTIVE" in the status feature
    Then the status feature saved status change is "ACTIVE"
