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

  Scenario: Delete an existing customer
    Given a customer with ID "cust-DEL-1" exists
    When the customer is deleted
    Then the customer can no longer be retrieved
