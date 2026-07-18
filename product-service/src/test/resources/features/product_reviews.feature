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
