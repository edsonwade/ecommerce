# Account Settings — Edit Own Data / Delete Own Account (Task 3)

**Date:** 2026-07-04
**Series:** Account-management task 3 of 3 (after register confirm-password and login forgot-password)
**Status:** Approved by user (backend + frontend incl. ProfilePage trim)

## Problem

- The auth-service `User` (login identity: email, password, name) has **no self-service endpoints**. `AuthController` covers only register/login/refresh/logout/forgot/reset; `UserManagementController` is ADMIN-only.
- The frontend `/account/profile` page edits the **customer-service** profile (name, email, address). Editing "email" there does **not** change the login email — auth and customer identities silently diverge.
- Delete-own-account exists nowhere (customer-service has `DELETE /customers/{id}`, but nothing touches the auth user and no UI offers it).

## Decisions (user-approved)

| Decision | Choice |
|---|---|
| Edit scope | Full: identity (name/email) + password via new auth self-service endpoints; profile page keeps address |
| Delete semantics | Soft delete + anonymize (GDPR-style); orders/payments kept intact |
| Roles | Edit for USER/SELLER/ADMIN; delete-own-account **USER only** (403 otherwise, UI hidden) — seller/admin deletion is a future task |
| Sync to customer-service | Async fail-open internal call (CustomerProvisioning pattern); **no Kafka** per auth rules |

## 1. auth-service: `AccountController` (`/api/v1/auth/account`)

All endpoints authenticated (no new permitAll). New `AccountService` in `application/`.

### `GET /me`
Returns `AccountResponse`: id, firstname, lastname, email, role, createdAt. Source of truth for the settings page.

### `PATCH /me` — edit firstname / lastname / email
- `currentPassword` **required when email changes** (re-auth for sensitive change); not required for name-only edits.
- Email uniqueness violation → 409 `auth.account.email.taken`.
- JWT subject = email (`UserDetailsAdapter.getUsername()` returns email; `JwtService` compares token subject to it), so an email change invalidates the caller's current access token. Therefore: on email change, **revoke all refresh tokens and return a fresh token pair** in the response. Name-only change returns the updated account, no new tokens.
- Response shape: `AccountUpdateResponse { account: AccountResponse, tokens: AuthResponse | null }` (tokens present only when email changed).

### `POST /change-password`
- Body: `{currentPassword, newPassword, confirmPassword}`.
- Wrong current password → 400 `auth.account.password.invalid` (no further oracle). Mismatch → 400 (same message-key style as the reset flow).
- On success: revoke all refresh tokens (same as `PasswordResetService`), return a fresh token pair so the user stays signed in. BCrypt cost from existing config (default 10).

### `DELETE /me`
- Body: `{password}`. `@PreAuthorize("hasRole('USER')")` — SELLER/ADMIN get 403.
- Soft delete + anonymize: `accountEnabled = false`, email → `deleted-<id>@removed.local`, firstname/lastname → `Deleted`/`User`, revoke all tokens. Returns **204**.
- Original email becomes free to register again. Login on the disabled account stays a generic 401 (no "account deleted" oracle).
- **No Flyway migration** — existing columns suffice.

## 2. Propagation to customer-service (async, fail-open)

Follows the `CustomerProvisioning` pattern: after auth DB commit, `@Async` on `authSideEffectsExecutor` (DiscardPolicy, never blocks the request thread):

- Name/email edit → new idempotent `PUT /api/v1/customers/internal/{id}` (updates firstname/lastname/email; no-op if the profile doesn't exist). Secured identically to the existing `/internal` provisioning endpoint.
- Delete → new idempotent `DELETE /api/v1/customers/internal/{id}` (204 even if already gone).

Settings changes never fail because customer-service is down; the profile catches up (idempotent retries safe).

## 3. Frontend

- New **`AccountSettingsPage`** (single shared component):
  - *Identity*: firstname/lastname/email fields; current-password field appears only when the email was edited.
  - *Change password*: current/new/confirm with the existing show/hide toggle.
  - *Danger zone* (rendered for USER only): delete account behind a confirm dialog requiring the password.
  - On email/password change: swap the returned token pair into `auth.store` (sessionStorage). On delete: clear session, toast, redirect home.
- Routes: `/account/settings` (customer layout) + same component reachable from SELLER and ADMIN layouts. **Read `WORKING_STATE_frontend.md` before touching sidebar/nav**; at most one nav entry per role; do not disturb required sidebar items, md-breakpoint/hamburger rule, or cart rules.
- **ProfilePage trim**: `/account/profile` becomes address-only; name/email shown read-only with an "Edit in account settings" link. (Otherwise it keeps writing name/email into customer-service and re-creates the divergence this task fixes.)
- API client: `authApi.getAccount / updateAccount / changePassword / deleteAccount`; MSW handlers for each.

## 4. Error handling summary

| Case | Response |
|---|---|
| Unauthenticated call to any `/account` endpoint | 401 |
| Email change / delete with wrong password | 400 `auth.account.password.invalid` |
| New email already taken | 409 `auth.account.email.taken` |
| Password confirm mismatch | 400 (mismatch message key) |
| DELETE as SELLER/ADMIN | 403 (UI also hides the section) |
| Login after delete | generic 401 invalid credentials |
| customer-service down during sync | auth change still succeeds; async side-effect logged + fail-open |

## 5. Testing (all three layers — mandatory)

- **auth-service**: `AccountServiceTest` (unit, `@Nested` per operation), controller slice tests (401 unauth, 403 delete as SELLER, 409 taken email, wrong-password 400, token rotation on email change), BDD `account-settings.feature` (edit name; edit email → login with new email OK, old email 401; change password; delete → login 401 → re-register same email 201).
- **customer-service**: unit + slice + BDD for the two internal endpoints, including idempotency (update missing profile = no-op; double delete = 204).
- **Frontend**: `tsc -b` + eslint + `npm run build`; MSW handlers; Playwright e2e spec for the settings flow (seed auth via **sessionStorage**, per the E2E regression lesson). Component vitest skipped on Windows (known MUI/framer crash) — verified via tsc + eslint.
- Claude runs `mvn test` / frontend builds; **user runs all docker rebuilds** for live proof.

## Out of scope (explicit)

- Seller/admin account deletion (product deactivation, last-admin protection) — future task.
- Email-change verification mail (confirm new address by link) — YAGNI for now; password re-auth is the guard.
- Any Kafka/broker involvement in auth — forbidden by standing rule.
