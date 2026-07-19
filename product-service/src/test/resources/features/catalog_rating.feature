Feature: Catalog average rating (Fase 7 Task 7.3 — denormalised counters, Decision A1)

  The storefront shows stars on product cards and on the product detail page. Those numbers are
  denormalised onto the product row and recomputed from source whenever a review is written or
  removed, so the catalogue never has to aggregate reviews at read time.

  Background:
    Given catalog rating: product 1 exists and is active

  Scenario: A product nobody reviewed shows no stars
    Then catalog rating: the catalog shows an average of "0.0" from 0 reviews

  Scenario: The first review sets the product's average
    When catalog rating: customer 42 posts a 5 star review for product 1
    Then catalog rating: the catalog shows an average of "5.0" from 1 reviews

  Scenario: Several reviews average out, rounded to one decimal
    When catalog rating: customer 42 posts a 5 star review for product 1
    And catalog rating: customer 43 posts a 4 star review for product 1
    And catalog rating: customer 44 posts a 4 star review for product 1
    Then catalog rating: the catalog shows an average of "4.3" from 3 reviews

  Scenario: Removing a review recalculates the average
    Given catalog rating: product 1 already has ratings 5 and 1
    When catalog rating: the review rated 1 is removed
    Then catalog rating: the catalog shows an average of "5.0" from 1 reviews

  Scenario: Removing the last review resets the product to no stars
    Given catalog rating: product 1 already has ratings 4
    When catalog rating: the review rated 4 is removed
    Then catalog rating: the catalog shows an average of "0.0" from 0 reviews

  # Decision A1 "no-evict": a review write refreshes the product detail but deliberately leaves the
  # cached catalogue pages alone, so GET /products latency never regresses. Cards catch up on TTL.
  Scenario: Writing a review refreshes the detail but keeps the catalogue pages cached
    Given catalog rating: the product detail and a catalogue page are cached
    When catalog rating: customer 42 posts a 5 star review for product 1
    Then catalog rating: the cached product detail was discarded
    And catalog rating: the cached catalogue page survived
