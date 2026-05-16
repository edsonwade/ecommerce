# ============================================================
# Feature: Product Search and Filter
# BDD Cucumber feature file — describes the search/filter
# functionality for the product catalog.
# ============================================================
@Search
Feature: Product catalog search and filter
  As a customer
  I want to search and filter products by name, description, and category
  So that I can find what I'm looking for quickly

  @Search
  Scenario: Search returns matching products by name
    Given the catalog has products named "Laptop" and "Headphones"
    When I search for "Laptop"
    Then the search results should contain 1 product
    And the result should include "Laptop"

  @Search
  Scenario: Search returns empty results when no products match
    Given the catalog has products named "Laptop" and "Headphones"
    When I search for "Webcam"
    Then the search results should be empty

  @Search
  Scenario: Search with no query returns all products
    Given the catalog has products named "Laptop" and "Headphones"
    When I search with no filters
    Then the search results should contain 2 products

  @Search
  Scenario: Filter by category returns only products in that category
    Given the catalog has a product "Laptop" in category 1 and "Headphones" in category 2
    When I filter by category 1
    Then the search results should contain 1 product
    And the result should include "Laptop"

  @Search
  Scenario: Combined search and category filter
    Given the catalog has a product "Gaming Laptop" in category 1 and "Office Laptop" in category 2
    When I search for "Laptop" and filter by category 1
    Then the search results should contain 1 product
    And the result should include "Gaming Laptop"
