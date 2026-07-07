@monitoring
Feature: Prometheus metrics scrape endpoint
  Prometheus polls /actuator/prometheus without credentials.
  The endpoint must be publicly readable or every scrape fails with 401
  and the service is reported as down.

  Scenario: Prometheus scrapes metrics without credentials
    When the monitoring system requests the metrics endpoint without credentials
    Then the metrics endpoint responds with HTTP 200
    And the metrics response contains Prometheus metrics

  Scenario: Business endpoints remain protected
    When an anonymous client requests a protected cart endpoint
    Then the protected endpoint responds with HTTP 401
