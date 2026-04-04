Feature: Notification Service Resilience

  Background:
    Given the notification service is running

  Scenario: Duplicate Kafka event is silently skipped
    Given a payment event for order "ORD-DUP-001" has already been processed on partition 0 at offset 10
    When the same payment event arrives again on partition 0 at offset 10
    Then no email is sent
    And the Kafka offset is acknowledged to avoid redelivery

  Scenario: Failed payment event is stored in DLQ MongoDB collection
    When a payment DLQ event for order "ORD-DLQ-001" arrives on topic "payment-topic.DLQ" partition 0 at offset 99
    Then the DLQ event is saved to MongoDB with topic "payment-topic.DLQ"

  Scenario: Failed order event is stored in DLQ MongoDB collection
    When an order DLQ event for order "ORD-DLQ-002" arrives on topic "order-topic.DLQ" partition 1 at offset 55
    Then the DLQ event is saved to MongoDB with topic "order-topic.DLQ"

  Scenario: Health endpoint reports Kafka as DOWN when broker is unreachable
    Given the Kafka broker is unreachable
    When the Kafka health indicator is checked
    Then the health status is "DOWN"
    And the health details contain an "error" field

  Scenario: Health endpoint reports SMTP as DOWN when mail server is unreachable
    Given the SMTP server is unreachable
    When the SMTP health indicator is checked
    Then the health status is "DOWN"
    And the health details contain an "error" field
