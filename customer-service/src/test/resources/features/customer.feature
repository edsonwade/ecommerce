Feature: Customer Management
  As an admin
  I want to manage customer records
  So that I can view, update, and delete them

  Scenario: Create a new customer successfully
    Given a valid customer request for "john.doe@example.com"
    And the email is not already in use
    When the customer is created
    Then a customer ID is returned
    And the customer details can be retrieved

  Scenario: Fail to create customer with duplicate email
    Given a valid customer request for "existing@example.com"
    And the email is already in use
    When the customer is created
    Then the system rejects the request with a duplicate email error

  Scenario: Ensure customer is idempotent — returns existing ID
    Given a customer with ID "cust-ENSURE-1" exists
    When the customer is ensured with ID "cust-ENSURE-1" and email "ensure@example.com"
    Then the returned customer ID is "cust-ENSURE-1"

  Scenario: Ensure customer creates new record when not found
    Given no customer with ID "cust-NEW-1" exists
    When the customer is ensured with ID "cust-NEW-1" and email "new@example.com"
    Then the returned customer ID is "cust-NEW-1"

  Scenario: Delete an existing customer
    Given a customer with ID "cust-DEL-1" exists
    When the customer is deleted
    Then the customer can no longer be retrieved

  # Phase 2 — Kafka event scenarios
  Scenario: Creating a customer publishes a customer.profile event
    Given a valid customer request for "kafka.create@example.com"
    And the email is not already in use
    When the customer is created
    Then a customer.profile CREATED event is published

  Scenario: user.registered event creates a customer profile when user is new
    Given no customer with ID "user-kafka-001" exists
    When a user.registered event is received for "user-kafka-001" with email "kafka.user@example.com"
    Then a customer profile is created for "user-kafka-001"

  Scenario: user.registered event is idempotent for existing customers
    Given a customer with ID "user-kafka-002" exists
    When a user.registered event is received for "user-kafka-002" with email "existing.user@example.com"
    Then no duplicate customer is created for "user-kafka-002"
