Feature: Account settings — edit own data and delete own account
  Self-service management of the login identity. Email changes rotate sessions,
  password changes revoke old sessions, and deletion is soft + anonymizing.

  Scenario: Update my name only
    Given a registered user "settings.name@bdd.com" with password "Passw0rd!1"
    When the user updates their name to "Novo" "Nome"
    Then the account response shows firstname "Novo" and no new tokens

  Scenario: Change my email and sign in with the new address
    Given a registered user "settings.email@bdd.com" with password "Passw0rd!1"
    When the user changes their email to "settings.email.new@bdd.com" using password "Passw0rd!1"
    Then the response contains a fresh token pair
    And login with the old email "settings.email@bdd.com" and password "Passw0rd!1" fails with 401
    And login with the new email "settings.email.new@bdd.com" and password "Passw0rd!1" succeeds

  Scenario: Changing email with the wrong password is rejected
    Given a registered user "settings.wrongpw@bdd.com" with password "Passw0rd!1"
    When the user changes their email to "settings.wrongpw.new@bdd.com" using password "WrongPass!9"
    Then the request fails with 400 and error code "auth.account.password.invalid"

  Scenario: Change my password
    Given a registered user "settings.chpw@bdd.com" with password "Passw0rd!1"
    When the user changes their password from "Passw0rd!1" to "NewPassw0rd!2"
    Then login with the new email "settings.chpw@bdd.com" and password "NewPassw0rd!2" succeeds
    And login with the old email "settings.chpw@bdd.com" and password "Passw0rd!1" fails with 401

  Scenario: A seller reads their live approval status from their own account
    # Backs the SPA poll that unlocks selling after approval without a re-login:
    # GET /account/me is a live DB read, so it always reflects the current sellerStatus.
    Given a self-registered seller "seller.status@bdd.com" with password "Passw0rd!1"
    When the seller fetches their account
    Then the account view shows sellerStatus "PENDING_APPROVAL"

  Scenario: A non-seller account exposes no sellerStatus field
    Given a registered user "plain.status@bdd.com" with password "Passw0rd!1"
    When the seller fetches their account
    Then the account view omits sellerStatus

  Scenario: Delete my account, then re-register with the same email
    Given a registered user "settings.delete@bdd.com" with password "Passw0rd!1"
    When the user deletes their account with password "Passw0rd!1"
    Then login with the old email "settings.delete@bdd.com" and password "Passw0rd!1" fails with 401
    And registering again with email "settings.delete@bdd.com" and password "Passw0rd!1" succeeds
