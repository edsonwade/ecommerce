Feature: Notification Processing
  As a customer
  I want to receive emails when I pay for and place an order
  So that I have confirmation of my actions

  Scenario: Process payment confirmation notification
    Given a payment confirmation message for order "REF-PAY-01" with amount 50.00
    When the payment notification is consumed from Kafka
    Then a PAYMENT_CONFIRMATION notification is saved to the database
    And an email is sent to the customer for the payment
    And the Kafka message is acknowledged

  Scenario: Process order confirmation notification
    Given an order confirmation message for order "REF-ORD-01" with amount 150.00
    When the order notification is consumed from Kafka
    Then an ORDER_CONFIRMATION notification is saved to the database
    And an email with a PDF invoice is sent to the customer
    And the Kafka message is acknowledged
