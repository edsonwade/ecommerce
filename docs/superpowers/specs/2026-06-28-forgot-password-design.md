# Forgot / Reset Password — Design (Task 2)

**Date:** 2026-06-28 · **Branch:** `fix/registers-logins-customers-pages`
**Scope:** Self-service password reset for all roles (USER/Customer, SELLER, ADMIN). Role-agnostic — keys off email.

## Decisions (from brainstorming)
- **Email delivery:** sent **directly from auth-service** on a bounded `@Async` executor (off the request thread, fail-open). No Kafka — preserves the auth hot-path rule.
- **Provider:** **MailHog now** (SMTP `mailhog:1025`, no auth) via Docker, but behind an **`EmailSender` interface** so a real transactional provider (Resend/SendGrid) is a drop-in `@Component` swap selected by `app.email.provider` — no call-site changes. The abstraction is mandatory, built now.
- **Token storage:** dedicated `password_reset_token` table storing only the **SHA-256 hash** of a 256-bit random token. Single-use, 30-min TTL. Raw token lives only in the email link.

## Flow
1. Login page → "Forgot password?" → `/forgot-password`.
2. `POST /api/v1/auth/forgot-password { email }` → **always 200** (no user enumeration). If an active user exists: invalidate prior unused tokens, generate token, store hash + expiry, fire async email with `…/reset-password?token=RAW`.
3. Email link → `/reset-password?token=…`.
4. `POST /api/v1/auth/reset-password { token, newPassword, confirmPassword }` → hash token, find unused+unexpired record, BCrypt-encode new password, mark token used, **revoke all the user's JWT sessions**. `400` on invalid/expired/used token or mismatch.

## Backend (auth-service)
- **V5 Flyway** `V5__add_password_reset_token.sql` — `password_reset_token(id, user_id FK→app_user, token_hash UNIQUE, expires_at, used_at NULL, created_at)`.
- **`PasswordResetToken`** entity, **`PasswordResetTokenRepository`** (`findByTokenHash`, delete/invalidate by user).
- **`PasswordResetService`** — `requestReset(email, origin)` + `resetPassword(token, newPassword, confirmPassword)`. Generates token (`SecureRandom` 32 bytes, Base64URL), SHA-256 hashes for storage, enforces TTL/single-use, revokes sessions on success, fail-open + no enumeration.
- **`EmailSender`** interface + **`SmtpEmailSender`** (`JavaMailSender` + Thymeleaf `password-reset.html`, `@Async` on `authSideEffectsExecutor`, failures logged not thrown).
- **DTOs:** `ForgotPasswordRequest(email)`, `ResetPasswordRequest(token, newPassword, confirmPassword)` with Bean Validation (email format, password ≥8, not-blank token).
- **Exception:** `InvalidPasswordResetTokenException` (400) → handled by `AuthGlobalExceptionHandler`.
- **Controller:** two methods on `AuthController`, both `permitAll` in `SecurityConfig`. Gateway already permits `/api/v1/auth/**` — no gateway change.
- **Config:** `spring.mail.*` + `app.password-reset.{token-ttl,reset-url-base}` + `app.email.{provider,from}` in `config-service/.../authentication-service.yml`; `MAIL_HOST`/`MAIL_PORT` env + `depends_on: mailhog` in `docker-compose.yml`; `spring-boot-starter-mail` + thymeleaf in `pom.xml`.
- **messages.properties:** new `auth.reset.*` keys.

## Frontend
- "Forgot password?" link on `LoginPage`.
- **`ForgotPasswordPage.tsx`** (email → always show "check your email" confirmation).
- **`ResetPasswordPage.tsx`** (reads `?token=`, new + confirm password reusing the Task-1 `.refine` pattern → success → redirect `/login`).
- Routes `/forgot-password`, `/reset-password` under `PublicLayout`; `authApi.forgotPassword` / `authApi.resetPassword`.

## Tests (all three layers — mandatory)
- **Unit** `PasswordResetServiceTest` (Mockito): token generated+hashed+stored+emailed; unknown email no-ops silently; valid reset updates password + marks used + revokes sessions; expired/used/invalid → exception; mismatch → exception.
- **Controller slice** `@WebMvcTest`: forgot-password always 200; reset-password 200/400; validation field errors.
- **BDD** `password_reset.feature` + steps: request reset (200, no enumeration), invalid token (400), mismatch (400).
- **Frontend:** build + lint green; extend `AuthFlow.test.tsx`.

## Security notes
- No user enumeration (constant 200 on forgot-password).
- Token stored hashed, single-use, 30-min TTL; reset revokes all sessions.
- Endpoints `permitAll`; CSRF disabled (stateless JWT) consistent with existing auth endpoints.
