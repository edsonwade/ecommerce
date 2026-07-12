Feature: Admin user management — create users with any role
  The platform ADMIN has global control over user accounts. Unlike public
  self-registration (which blocks ADMIN), the admin endpoint may assign any
  role, and the created user can immediately authenticate.

  Scenario: Admin creates a SELLER who can then log in
    Given the platform admin is logged in
    When the admin creates a user "created.seller@bdd.com" with password "SellerPass1!" and role "SELLER"
    Then the user creation succeeds with role "SELLER"
    And login with the created email and password "SellerPass1!" succeeds with role "SELLER"

  Scenario: Admin creates another ADMIN
    Given the platform admin is logged in
    When the admin creates a user "created.admin@bdd.com" with password "AdminPass1!" and role "ADMIN"
    Then the user creation succeeds with role "ADMIN"

  Scenario: Duplicate email is rejected
    Given the platform admin is logged in
    When the admin creates a user "created.dup@bdd.com" with password "Password1!" and role "USER"
    And the admin creates the same user again with password "Password1!" and role "USER"
    Then the user creation fails with 409 and error code "auth.user.already.exists"

  Scenario: A regular user cannot create users
    Given a registered regular user "regular.actor@bdd.com" with password "Password1!"
    When that user tries to create a user "sneaky@bdd.com" with password "Password1!" and role "USER"
    Then the user creation fails with 403

  Scenario: Public registration still blocks ADMIN
    When someone registers publicly as "public.admin@bdd.com" with password "Password1!" and role "ADMIN"
    Then the user creation fails with 400 and error code "auth.register.admin.denied"

  Scenario: Admin renames a user
    Given the platform admin is logged in
    And the admin creates a user "renamed.user@bdd.com" with password "Password1!" and role "USER"
    When the admin renames that user to "Novo" "Nome"
    Then the managed user response shows firstname "Novo" and lastname "Nome"

  Scenario: Admin deactivates a user, who can no longer log in, then reactivates
    Given the platform admin is logged in
    And the admin creates a user "disabled.user@bdd.com" with password "Password1!" and role "USER"
    When the admin sets that user's status to disabled
    Then login with the created email and password "Password1!" fails with 401
    When the admin sets that user's status to enabled
    Then login with the created email and password "Password1!" succeeds with role "USER"

  Scenario: Admin deletes a user, who can no longer log in
    Given the platform admin is logged in
    And the admin creates a user "deleted.user@bdd.com" with password "Password1!" and role "USER"
    When the admin deletes that user
    Then login with the created email and password "Password1!" fails with 401

  Scenario: Admin-created seller is born approved
    Given the platform admin is logged in
    When the admin creates a user "approved.seller@bdd.com" with password "SellerPass1!" and role "SELLER"
    Then the user creation succeeds with role "SELLER"
    And the user creation shows seller status "APPROVED"

  Scenario: Self-registered seller is born pending approval
    When someone registers publicly as seller "pending.seller@bdd.com" with password "SellerPass1!"
    Then the registration response shows seller status "PENDING_APPROVAL"

  Scenario: Admin approves a pending seller who keeps their login
    Given the platform admin is logged in
    And someone registers publicly as seller "flow.seller@bdd.com" with password "SellerPass1!"
    When the admin sets that seller's status to "APPROVED"
    Then the seller status response shows "APPROVED"
    And login with the created email and password "SellerPass1!" succeeds with role "SELLER"

  Scenario: Admin suspends a seller
    Given the platform admin is logged in
    And the admin creates a user "suspend.seller@bdd.com" with password "SellerPass1!" and role "SELLER"
    When the admin sets that seller's status to "SUSPENDED"
    Then the seller status response shows "SUSPENDED"

  Scenario: Seller status does not apply to a regular user
    Given the platform admin is logged in
    And the admin creates a user "plain.target@bdd.com" with password "Password1!" and role "USER"
    When the admin sets that seller's status to "APPROVED"
    Then the user creation fails with 400 and error code "auth.seller.status.not.seller"
