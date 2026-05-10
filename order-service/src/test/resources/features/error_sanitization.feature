Feature: API Error Sanitization
  As a security-conscious administrator
  I want all API error responses to be sanitized
  So that internal system details like UUID references are not exposed to end users

  Scenario: A generic unhandled error occurs
    Given the service encounters an unexpected runtime error
    When the system returns the error response
    Then the response status should be 500
    And the error message should be "An unexpected error occurred. Please try again later."
    And the error message should not contain "Reference:" or a UUID pattern
    And the response body should not include internal tracking fields like "requestId"
