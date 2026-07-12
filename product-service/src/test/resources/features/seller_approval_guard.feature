Feature: Seller approval write-guard
  A marketplace SELLER may only manage products once an administrator has approved
  their account. The guard reads the sellerStatus JWT claim: PENDING_APPROVAL and
  SUSPENDED sellers are blocked from product writes; APPROVED sellers work normally.
  A seller whose token predates the approval flow (no claim) is grandfathered, and
  ADMIN is never gated.

  Scenario: A pending seller cannot create a product
    Given a seller authenticated with seller status "PENDING_APPROVAL"
    When the seller creates a product "Widget"
    Then the product write is forbidden with reason "seller.not.approved"

  Scenario: A suspended seller cannot update their own product
    Given a seller authenticated with seller status "SUSPENDED"
    And the seller owns product 1 named "Widget"
    When the seller updates product 1 to "Tampered"
    Then the product write is forbidden with reason "seller.suspended"

  Scenario: An approved seller creates a product normally
    Given a seller authenticated with seller status "APPROVED"
    When the seller creates a product "Widget"
    Then the product write succeeds

  Scenario: A seller with an old token (no status claim) is grandfathered
    Given a seller authenticated with no seller status claim
    When the seller creates a product "Widget"
    Then the product write succeeds

  Scenario: An admin is never gated by seller status
    Given an admin is authenticated
    When the seller creates a product "Widget"
    Then the product write succeeds
