Feature: Product reviews (F7)
  A customer may review a product only after a verified purchase, at most once per product.
  Admins moderate (delete any review); authors may delete their own; nobody else can.

  Scenario: A verified buyer can post a review
    Given review feature: customer 42 has a verified purchase of product 1
    When review feature: customer 42 posts a 5 star review for product 1
    Then review feature: the review is created

  Scenario: A shopper who never bought the product cannot review it
    Given review feature: customer 42 has NOT purchased product 1
    When review feature: customer 42 posts a 5 star review for product 1
    Then review feature: the review is rejected as not-purchased

  Scenario: A buyer cannot review the same product twice
    Given review feature: customer 42 has a verified purchase of product 1
    And review feature: customer 42 has already reviewed product 1
    When review feature: customer 42 posts a 5 star review for product 1
    Then review feature: the review is rejected as duplicate

  Scenario: The review is rejected when purchase verification is unavailable
    Given review feature: the purchase-verification service is unavailable
    When review feature: customer 42 posts a 5 star review for product 1
    Then review feature: the review is rejected as verification-unavailable

  Scenario: An admin can delete any review
    Given review feature: a review 7 by customer 42 on product 1 exists
    When review feature: admin 99 deletes review 7
    Then review feature: the review is deleted

  Scenario: The author can delete their own review
    Given review feature: a review 7 by customer 42 on product 1 exists
    When review feature: customer 42 deletes review 7
    Then review feature: the review is deleted

  Scenario: Another shopper cannot delete someone else's review
    Given review feature: a review 7 by customer 42 on product 1 exists
    When review feature: customer 55 deletes review 7
    Then review feature: the delete is rejected as forbidden

  # Task 7.4a — the storefront asks up front whether to show the review form,
  # instead of letting the shopper write one only to have it rejected.
  Scenario: A verified buyer who has not reviewed yet is offered the form
    Given review feature: customer 42 has a verified purchase of product 1
    When review feature: customer 42 checks whether they can review product 1
    Then review feature: the verdict is ELIGIBLE

  Scenario: A shopper who never bought the product is not offered the form
    Given review feature: customer 42 has NOT purchased product 1
    When review feature: customer 42 checks whether they can review product 1
    Then review feature: the verdict is NOT_PURCHASED

  Scenario: A buyer who already reviewed sees their existing review instead of the form
    Given review feature: customer 42 already has review 7 on product 1
    When review feature: customer 42 checks whether they can review product 1
    Then review feature: the verdict is ALREADY_REVIEWED
    And review feature: the verdict carries the existing review

  # Unlike the POST, this read must not fail the page when order-service is down.
  Scenario: The product page still renders when purchase verification is down
    Given review feature: the purchase-verification service is unavailable
    When review feature: customer 42 checks whether they can review product 1
    Then review feature: the verdict is VERIFICATION_UNAVAILABLE
