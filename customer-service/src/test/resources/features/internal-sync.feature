Feature: Internal identity sync and delete (service-to-service)

  Scenario: Auth-service pushes a new name and email to an existing profile
    Given a customer profile exists with id "sync-1" and email "before@bdd.com"
    When the internal sync updates id "sync-1" to name "After" "Sync" email "after@bdd.com"
    Then the profile "sync-1" has firstname "After" and email "after@bdd.com"

  Scenario: Sync for a missing profile is a silent no-op
    When the internal sync updates id "ghost-1" to name "No" "One" email "ghost@bdd.com"
    Then the internal call succeeded with status 204

  Scenario: Delete is idempotent
    Given a customer profile exists with id "del-1" and email "del@bdd.com"
    When the internal delete removes id "del-1"
    And the internal delete removes id "del-1"
    Then the internal call succeeded with status 204
