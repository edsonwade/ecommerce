Feature: Product API Endpoints

  Scenario: Get all products - success
    Given the following products exist:
      | id | name       | description   | price | quantity |
      | 1  | Product 1  | Description 1 | 100.0 | 100.0    |
    When I send a GET request to "/api/v1/products"
    Then the response status should be 200
    And the response should contain the following products:
      | id | name      | description   |
      | 1  | Product 1 | Description 1 |

  Scenario: Get product by ID - success
    Given the following product exists:
      | id | name       | description   | price | quantity |
      | 1  | Product 1  | Description 1 | 100.0 | 100.0    |
    When I send a GET request to "/api/v1/products/1"
    Then the response status should be 200
    And the response should contain the product:
      | id | name      | description   |
      | 1  | Product 1 | Description 1 |

  Scenario: Get product by ID - failure
    Given the product with ID 2 does not exist
    When I send a GET request to "/api/v1/products/2"
    Then the response status should be 404

  Scenario: Create product - success
    Given the following product request:
      | name       | description   | price | quantity |
      | Product 1  | Description 1 | 100.0 | 100.0    |
    When I send a POST request to "/api/v1/products" with the request body:
      """
      {
        "name": "Product 1",
        "description": "Description 1",
        "price": 100.0,
        "quantity": 100.0
      }
      """
    Then the response status should be 201
    And the response should contain the product:
      | name      | description   |
      | Product 1 | Description 1 |

  Scenario: Create product - failure
    Given the following product request:
      | name       | description   | price | quantity |
      |            | Description 1 | 100.0 | 100.0    |
    When I send a POST request to "/api/v1/products" with the request body:
      """
      {
        "name": "",
        "description": "Description 1",
        "price": 100.0,
        "quantity": 100.0
      }
      """
    Then the response status should be 400

  Scenario: Update product - success
    Given the following product exists:
      | id | name       | description   | price | quantity |
      | 1  | Product 1  | Description 1 | 100.0 | 100.0    |
    And the following update request:
      | name      | description      | price | quantity |
      | New Name  | New Description  | 200.0 | 50.0     |
    When I send a PUT request to "/api/v1/products/1" with the request body:
      """
      {
        "name": "New Name",
        "description": "New Description",
        "price": 200.0,
        "quantity": 50.0
      }
      """
    Then the response status should be 200
    And the response should contain the product:
      | name      | description     |
      | New Name  | New Description |

  Scenario: Update product - failure
    Given the product with ID 2 does not exist
    And the following update request:
      | name      | description      | price | quantity |
      | New Name  | New Description  | 200.0 | 50.0     |
    When I send a PUT request to "/api/v1/products/2" with the request body:
      """
      {
        "name": "New Name",
        "description": "New Description",
        "price": 200.0,
        "quantity": 50.0
      }
      """
    Then the response status should be 404

  Scenario: Delete product - success
    Given the following product exists:
      | id | name       | description   | price | quantity |
      | 1  | Product 1  | Description 1 | 100.0 | 100.0    |
    When I send a DELETE request to "/api/v1/products/1"
    Then the response status should be 204

  Scenario: Delete product - failure
    Given the product with ID 2 does not exist
    When I send a DELETE request to "/api/v1/products/2"
    Then the response status should be 404
