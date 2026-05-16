@auth
Feature: Authentication Service
  As a client application
  I want to register, log in, refresh tokens, and log out
  So that I can access protected resources with JWT-based security

  Background:
    Given the authentication service is running

  # =========================================================
  # User Registration
  # =========================================================

  @registration @happy-path
  Scenario: Successful user registration returns a JWT token pair
    Given no user exists with email "bdd.register@example.com"
    When I register with the following details:
      | firstname | lastname | email                       | password   |
      | Alice     | Smith    | bdd.register@example.com    | Secure123! |
    Then the response status is 201
    And the response contains a valid access token
    And the response contains a valid refresh token
    And the response token type is "Bearer"
    And the response role is "USER"
    And the response email is "bdd.register@example.com"

  @registration @negative
  Scenario: Registration with duplicate email returns 409 Conflict
    Given a user already exists with email "existing.bdd@example.com"
    When I register with the following details:
      | firstname | lastname | email                    | password   |
      | Bob       | Jones    | existing.bdd@example.com | Secure123! |
    Then the response status is 409
    And the error code is "auth.user.already.exists"
    And the error message contains "already exists"

  @registration @negative
  Scenario: Registration without email returns 400 Bad Request
    When I register with the following details:
      | firstname | lastname | password   |
      | Alice     | Smith    | Secure123! |
    Then the response status is 400
    And the error code is "auth.validation.failed"
    And the field error "email" is present

  @registration @negative
  Scenario: Registration with invalid email format returns 400 Bad Request
    When I register with the following details:
      | firstname | lastname | email       | password   |
      | Alice     | Smith    | not-a-email | Secure123! |
    Then the response status is 400
    And the field error "email" is present

  @registration @negative
  Scenario: Registration with password shorter than 8 characters returns 400 Bad Request
    When I register with the following details:
      | firstname | lastname | email                    | password |
      | Alice     | Smith    | short.pass@example.com   | short    |
    Then the response status is 400
    And the field error "password" is present

  @registration @happy-path @seller
  Scenario: Seller registration returns SELLER role in token response
    Given no user exists with email "bdd.seller.register@example.com"
    When I register with the following details:
      | firstname | lastname | email                           | password   | role   |
      | Charlie   | Trader   | bdd.seller.register@example.com | Secure123! | SELLER |
    Then the response status is 201
    And the response contains a valid access token
    And the response contains a valid refresh token
    And the response role is "SELLER"

  @registration @negative @security
  Scenario: Self-registration as ADMIN is rejected with 400 Bad Request
    Given no user exists with email "bdd.admin.self.register@example.com"
    When I register with the following details:
      | firstname | lastname | email                              | password   | role  |
      | Evil      | Hacker   | bdd.admin.self.register@example.com | Secure123! | ADMIN |
    Then the response status is 400
    And the error code is "auth.register.admin.denied"

  @registration @negative
  Scenario: Registration with an invalid role value returns 400 Bad Request
    When I register with the following details:
      | firstname | lastname | email                        | password   | role       |
      | Dave      | Jones    | bdd.invalidrole@example.com  | Secure123! | SUPERADMIN |
    Then the response status is 400
    And the error code is "auth.register.invalid.role"

  # =========================================================
  # User Login
  # =========================================================

  @login @happy-path
  Scenario: Successful login with valid credentials returns JWT token pair
    Given a user exists with email "bdd.login@example.com" and password "LoginPass99"
    When I log in with email "bdd.login@example.com" and password "LoginPass99"
    Then the response status is 200
    And the response contains a valid access token
    And the response contains a valid refresh token
    And the response token type is "Bearer"

  @login @negative
  Scenario: Login with wrong password returns 401 Unauthorized
    Given a user exists with email "bdd.wrongpwd@example.com" and password "CorrectPass1"
    When I log in with email "bdd.wrongpwd@example.com" and password "WrongPassword"
    Then the response status is 401
    And the error code is "auth.login.invalid.credentials"
    And the error message is "Invalid email or password. Please check and try again."

  @login @negative
  Scenario: Login with non-existent email returns 401 Unauthorized
    When I log in with email "ghost.user@nowhere.com" and password "anyPassword"
    Then the response status is 401
    And the error code is "auth.login.invalid.credentials"

  @login @negative
  Scenario: Login with empty body returns 400 Bad Request
    When I send an empty login request
    Then the response status is 400
    And the error code is "auth.validation.failed"
    And the field error "email" is present
    And the field error "password" is present

  # =========================================================
  # Token Refresh
  # =========================================================

  @refresh @happy-path
  Scenario: Valid refresh token returns a new JWT token pair
    Given a user is registered and logged in with email "bdd.refresh@example.com"
    When I refresh the token using the refresh token
    Then the response status is 200
    And the response contains a valid access token
    And the new access token is different from the original access token

  @refresh @negative
  Scenario: Access token used as refresh token returns 401 Unauthorized
    Given a user is registered and logged in with email "bdd.accessasrefresh@example.com"
    When I refresh the token using the access token instead of the refresh token
    Then the response status is 401

  @refresh @negative
  Scenario: Missing Authorization header on refresh returns 401 Unauthorized
    When I call the refresh endpoint without any authorization header
    Then the response status is 401

  @refresh @negative
  Scenario: Completely malformed token string on refresh returns 401 Unauthorized
    When I call the refresh endpoint with token "garbage.token.string"
    Then the response status is 401
    And the error code is "auth.jwt.invalid"

  @refresh @negative
  Scenario: Revoked refresh token after logout returns 401 Unauthorized
    Given a user is registered and logged in with email "bdd.revokerefresh@example.com"
    When I log out using the current access token
    And I refresh the token using the refresh token
    Then the response status is 401
    And the error code is "auth.token.refresh.invalid"

  # =========================================================
  # Logout
  # =========================================================

  @logout @happy-path
  Scenario: Authenticated user can log out successfully
    Given a user is registered and logged in with email "bdd.logout@example.com"
    When I log out using the current access token
    Then the response status is 204

  @logout @negative
  Scenario: Logout without any token returns 401 Unauthorized
    When I call the logout endpoint without any authorization header
    Then the response status is 401

  @logout @security
  Scenario: Access token is revoked after logout — reuse is rejected
    Given a user is registered and logged in with email "bdd.reusetoken@example.com"
    When I log out using the current access token
    And I attempt to log out again with the same access token
    Then the response status is 401

  # =========================================================
  # Security Edge Cases
  # =========================================================

  @security @edge-case
  Scenario: Unauthenticated request to protected endpoint returns 401
    When I call the logout endpoint without any authorization header
    Then the response status is 401

  @security @edge-case
  Scenario: Error response structure matches API contract
    When I log in with email "bad@example.com" and password "wrongPass"
    Then the response status is 401
    And the error response contains field "timestamp"
    And the error response contains field "status"
    And the error response contains field "errorCode"
    And the error response contains field "message"
    And the error response contains field "path"

  # =========================================================
  # Admin Account Bootstrap
  # =========================================================

  @admin @happy-path
  Scenario: Seeded admin account can log in with canonical credentials
    Given the admin account "admin@obsidian.com" has been seeded by AdminBootstrapRunner
    When I log in with email "admin@obsidian.com" and password "Admin@123!"
    Then the response status is 200
    And the response contains a valid access token
    And the response role is "ADMIN"

  @admin @negative
  Scenario: Admin login with wrong password returns 401 Unauthorized
    Given the admin account "admin@obsidian.com" has been seeded by AdminBootstrapRunner
    When I log in with email "admin@obsidian.com" and password "WrongPass"
    Then the response status is 401
    And the error code is "auth.login.invalid.credentials"

  # =========================================================
  # Full Authentication Lifecycle
  # =========================================================

  @lifecycle @happy-path
  Scenario: Full authentication lifecycle completes successfully
    Given no user exists with email "bdd.lifecycle@example.com"
    When I complete the full auth lifecycle for "bdd.lifecycle@example.com"
    Then the lifecycle completes without errors
    And all tokens are properly rotated and invalidated
