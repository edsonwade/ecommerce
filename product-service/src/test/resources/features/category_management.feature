Feature: Category management (Fase 4 — Task 4.1)
  An administrator manages the category catalogue. Names are unique
  (case-insensitive); a category still referenced by products cannot be
  deleted; missing categories surface as not-found.

  Scenario: creating a category with a fresh name succeeds
    Given the category feature is ready
    When the category feature creates a category named "Cable Trays" described as "Cable management"
    Then the category feature saved a category named "Cable Trays"
    And the created category feature response is named "Cable Trays"

  Scenario: creating a category trims the name and stamps a tenant
    Given the category feature is ready
    When the category feature creates a category named "  Padded Name  " described as "trim me"
    Then the category feature saved a category named "Padded Name"
    And the category feature saved category has a non-blank tenant

  Scenario: a duplicate name is rejected as a conflict
    Given the category feature is ready
    And the category feature already has a category named "Keyboards"
    When the category feature creates a category named "keyboards" expecting a conflict
    Then the category feature create is rejected with reason "category.name.exists"

  Scenario: renaming an existing category succeeds
    Given the category feature is ready
    And the category feature has a stored category 5 named "Old Name"
    When the category feature renames category 5 to "New Name"
    Then the category feature saved a category named "New Name"

  Scenario: updating a missing category fails as not found
    Given the category feature is ready
    When the category feature renames category 99 to "Ghost" expecting a failure
    Then the category feature update fails as not found

  Scenario: deleting an unreferenced category succeeds
    Given the category feature is ready
    And the category feature has a stored category 7 named "Disposable"
    And the category feature category 7 has 0 referencing products
    When the category feature deletes category 7
    Then the category feature deleted category 7

  Scenario: deleting a category still referenced by products is rejected
    Given the category feature is ready
    And the category feature has a stored category 8 named "In Use"
    And the category feature category 8 has 3 referencing products
    When the category feature deletes category 8 expecting a conflict
    Then the category feature delete is rejected with reason "category.delete.has.products"
