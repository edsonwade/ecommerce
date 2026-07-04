# Account Settings (Edit Own Data / Delete Own Account) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Spec:** `docs/superpowers/specs/2026-07-04-account-settings-design.md`

**Goal:** Self-service account management — authenticated users edit their own name/email/password on auth-service (the login identity), USER-role accounts can soft-delete themselves, and changes propagate async fail-open to customer-service; new AccountSettingsPage in the frontend.

**Architecture:** New `AccountController`/`AccountService` in auth-service following the existing controller→service→repository layering. Identity changes revoke sessions and mint fresh token pairs (JWT subject = email). Propagation to customer-service reuses the `CustomerProvisioning` `@Async` fail-open pattern via two new idempotent `/internal` endpoints. Frontend adds one shared settings page and trims ProfilePage to address-only.

**Tech Stack:** Spring Boot 3, Spring Security (JWT), JPA/Postgres, OpenFeign, JUnit5+Mockito, Cucumber+RestAssured, React 19 + MUI v9 + RHF/Zod + TanStack Query + Zustand, Playwright.

## Global Constraints

- **NO git actions** — never branch/commit/push; the user handles all git (overrides the skill's commit steps; memory `feedback_no_git_actions`).
- **NO Docker** — code changes + `mvn test` + frontend build only; user runs rebuilds (memory `feedback_never_run_docker`).
- **No Kafka / no blocking side-effects on auth paths** — propagation must stay `@Async` on `AsyncConfig.AUTH_SIDE_EFFECTS_EXECUTOR`, fail-open (memory `feedback_auth_hotpath_no_kafka`).
- **All three test layers per backend change**: unit + controller slice + BDD Cucumber, in the same change (memory `feedback_always_write_tests_for_new_code`).
- **MUI v9**: `slotProps={{ htmlInput: {...} }}`, never `inputProps`.
- **Frontend verification** = `cd frontend && npm run build` (tsc -b && vite build) + `npm run lint` + `npx playwright test`. DOM/component vitest does NOT run on this Windows box.
- Tests foreground, correct directory, never background+monitor (memory `feedback_test_execution`).
- Error body shape (auth): `{timestamp,status,error,errorCode,message,path}` via `AuthGlobalExceptionHandler.build()`.
- Message keys live in `authentication-service/src/main/resources/messages.properties`; exceptions carry `(resolvedMessage, messageKey)` and extend `AuthBaseException`.
- Frontend nav/layout rules: permanent sidebar from `md` up, temporary drawer + hamburger below `md`, never auto-open; cart only for `role==='USER'`; required sidebar items in `SELLER_NAV`/`ADMIN_NAV` must remain (memory `WORKING_STATE_frontend.md`).

---

### Task 1: auth-service — DTOs, exception, messages, `AccountService.getAccount` + `updateAccount` (unit tests)

**Files:**
- Create: `authentication-service/src/main/java/code/with/vanilson/authentication/application/AccountResponse.java`
- Create: `authentication-service/src/main/java/code/with/vanilson/authentication/application/UpdateAccountRequest.java`
- Create: `authentication-service/src/main/java/code/with/vanilson/authentication/application/ChangePasswordRequest.java`
- Create: `authentication-service/src/main/java/code/with/vanilson/authentication/application/DeleteAccountRequest.java`
- Create: `authentication-service/src/main/java/code/with/vanilson/authentication/application/AccountUpdateResponse.java`
- Create: `authentication-service/src/main/java/code/with/vanilson/authentication/exception/InvalidAccountPasswordException.java`
- Create: `authentication-service/src/main/java/code/with/vanilson/authentication/application/AccountService.java`
- Modify: `authentication-service/src/main/java/code/with/vanilson/authentication/exception/AuthGlobalExceptionHandler.java` (add one handler)
- Modify: `authentication-service/src/main/resources/messages.properties` (add 3 keys)
- Test: `authentication-service/src/test/java/code/with/vanilson/authentication/application/AccountServiceTest.java`

**Interfaces:**
- Consumes: `UserRepository` (`findById`, `existsByEmail`, `save`), `TokenRepository.revokeAllUserTokens(Long)`, `JwtService.generateAccessToken(User)/generateRefreshToken(User)`, `RefreshTokenService.persistTokenPair(User, String, String)`, `CustomerProvisioning` (Task 3 adds the two new methods; in this task inject it and call `syncCustomerProfile` — create the method stub in Task 1 Step 3 so it compiles, full body in Task 3), `PasswordEncoder.matches/encode`, `MessageSource`.
- Produces: `AccountService.getAccount(Long): AccountResponse`, `AccountService.updateAccount(Long, UpdateAccountRequest): AccountUpdateResponse` — used by Task 4 controller. `AccountResponse(Long id, String firstname, String lastname, String email, String role, java.time.LocalDateTime createdAt)`. `AccountUpdateResponse(AccountResponse account, AuthResponse tokens)` — `tokens` null unless email changed.

- [ ] **Step 1: Write the failing unit tests**

Create `AccountServiceTest` mirroring the project style (`@Nested` per operation, Mockito, AssertJ):

```java
package code.with.vanilson.authentication.application;

import code.with.vanilson.authentication.domain.Role;
import code.with.vanilson.authentication.domain.User;
import code.with.vanilson.authentication.exception.InvalidAccountPasswordException;
import code.with.vanilson.authentication.exception.UserAlreadyExistsException;
import code.with.vanilson.authentication.infrastructure.JwtService;
import code.with.vanilson.authentication.infrastructure.TokenRepository;
import code.with.vanilson.authentication.infrastructure.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountService")
class AccountServiceTest {

    @Mock UserRepository       userRepository;
    @Mock TokenRepository      tokenRepository;
    @Mock JwtService           jwtService;
    @Mock PasswordEncoder      passwordEncoder;
    @Mock RefreshTokenService  refreshTokenService;
    @Mock CustomerProvisioning customerProvisioning;
    @Mock MessageSource        messageSource;

    @InjectMocks AccountService service;

    private User user;

    @BeforeEach
    void setUp() {
        lenient().when(messageSource.getMessage(anyString(), any(), any()))
                 .thenAnswer(inv -> inv.getArgument(0)); // key echoes back as message
        user = User.builder()
                .id(7L).firstname("Ana").lastname("Silva")
                .email("ana@x.com").password("$2a$10$hash")
                .role(Role.USER).tenantId("default")
                .accountEnabled(true).accountLocked(false)
                .build();
    }

    @Nested @DisplayName("GetAccount")
    class GetAccount {
        @Test
        void returns_account_view_of_the_user() {
            when(userRepository.findById(7L)).thenReturn(Optional.of(user));
            AccountResponse res = service.getAccount(7L);
            assertThat(res.id()).isEqualTo(7L);
            assertThat(res.email()).isEqualTo("ana@x.com");
            assertThat(res.role()).isEqualTo("USER");
        }
    }

    @Nested @DisplayName("UpdateAccount")
    class UpdateAccount {
        @Test
        void name_only_change_saves_without_new_tokens_and_syncs_profile() {
            when(userRepository.findById(7L)).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            AccountUpdateResponse res = service.updateAccount(7L,
                    new UpdateAccountRequest("Ana Maria", "Silva", "ana@x.com", null));

            assertThat(res.tokens()).isNull();
            assertThat(res.account().firstname()).isEqualTo("Ana Maria");
            verify(customerProvisioning).syncCustomerProfile(any(User.class));
            verify(tokenRepository, never()).revokeAllUserTokens(any());
        }

        @Test
        void email_change_with_valid_password_rotates_tokens() {
            when(userRepository.findById(7L)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("Secret123!", "$2a$10$hash")).thenReturn(true);
            when(userRepository.existsByEmail("new@x.com")).thenReturn(false);
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
            when(jwtService.generateAccessToken(any(User.class))).thenReturn("access");
            when(jwtService.generateRefreshToken(any(User.class))).thenReturn("refresh");

            AccountUpdateResponse res = service.updateAccount(7L,
                    new UpdateAccountRequest("Ana", "Silva", "new@x.com", "Secret123!"));

            assertThat(res.tokens()).isNotNull();
            assertThat(res.tokens().accessToken()).isEqualTo("access");
            assertThat(res.account().email()).isEqualTo("new@x.com");
            verify(tokenRepository).revokeAllUserTokens(7L);
            verify(refreshTokenService).persistTokenPair(any(User.class), eq("access"), eq("refresh"));
            verify(customerProvisioning).syncCustomerProfile(any(User.class));
        }

        @Test
        void email_change_with_wrong_password_is_rejected() {
            when(userRepository.findById(7L)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrong", "$2a$10$hash")).thenReturn(false);

            assertThatThrownBy(() -> service.updateAccount(7L,
                    new UpdateAccountRequest("Ana", "Silva", "new@x.com", "wrong")))
                    .isInstanceOf(InvalidAccountPasswordException.class);
            verify(userRepository, never()).save(any());
        }

        @Test
        void email_change_without_password_is_rejected() {
            when(userRepository.findById(7L)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> service.updateAccount(7L,
                    new UpdateAccountRequest("Ana", "Silva", "new@x.com", null)))
                    .isInstanceOf(InvalidAccountPasswordException.class);
        }

        @Test
        void email_change_to_taken_address_is_409() {
            when(userRepository.findById(7L)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("Secret123!", "$2a$10$hash")).thenReturn(true);
            when(userRepository.existsByEmail("taken@x.com")).thenReturn(true);

            assertThatThrownBy(() -> service.updateAccount(7L,
                    new UpdateAccountRequest("Ana", "Silva", "taken@x.com", "Secret123!")))
                    .isInstanceOf(UserAlreadyExistsException.class);
            verify(userRepository, never()).save(any());
        }

        @Test
        void same_email_different_case_is_not_an_email_change() {
            when(userRepository.findById(7L)).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            AccountUpdateResponse res = service.updateAccount(7L,
                    new UpdateAccountRequest("Ana", "Silva", "ANA@X.COM", null));

            assertThat(res.tokens()).isNull();
            verify(tokenRepository, never()).revokeAllUserTokens(any());
        }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run (repo root): `mvn test -pl authentication-service -Dtest=AccountServiceTest`
Expected: COMPILATION ERROR (AccountService / DTOs don't exist yet).

- [ ] **Step 3: Implement DTOs, exception, messages, service, provisioning stub**

`AccountResponse.java`:
```java
package code.with.vanilson.authentication.application;

import code.with.vanilson.authentication.domain.User;

import java.time.LocalDateTime;

/** AccountResponse — the authenticated user's own identity view (never another user's). */
public record AccountResponse(
        Long id,
        String firstname,
        String lastname,
        String email,
        String role,
        LocalDateTime createdAt
) {
    public static AccountResponse from(User user) {
        return new AccountResponse(user.getId(), user.getFirstname(), user.getLastname(),
                user.getEmail(), user.getRole().name(), user.getCreatedAt());
    }
}
```

`UpdateAccountRequest.java`:
```java
package code.with.vanilson.authentication.application;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * UpdateAccountRequest — payload for PATCH /api/v1/auth/account/me.
 * currentPassword is only required when the email is being changed (checked in the service,
 * not here, because "required" depends on whether the email actually differs).
 */
public record UpdateAccountRequest(
        @NotBlank(message = "{auth.validation.firstname.required}")
        String firstname,

        @NotBlank(message = "{auth.validation.lastname.required}")
        String lastname,

        @NotBlank(message = "{auth.validation.email.required}")
        @Email(message = "{auth.validation.email.invalid}")
        String email,

        String currentPassword
) {}
```

`ChangePasswordRequest.java`:
```java
package code.with.vanilson.authentication.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** ChangePasswordRequest — payload for POST /api/v1/auth/account/change-password. */
public record ChangePasswordRequest(
        @NotBlank(message = "{auth.validation.password.required}")
        String currentPassword,

        @NotBlank(message = "{auth.validation.password.required}")
        @Size(min = 8, message = "{auth.validation.password.length}")
        String newPassword,

        @NotBlank(message = "{auth.validation.password.required}")
        String confirmPassword
) {}
```

`DeleteAccountRequest.java`:
```java
package code.with.vanilson.authentication.application;

import jakarta.validation.constraints.NotBlank;

/** DeleteAccountRequest — payload for DELETE /api/v1/auth/account/me (password re-auth). */
public record DeleteAccountRequest(
        @NotBlank(message = "{auth.validation.password.required}")
        String password
) {}
```

`AccountUpdateResponse.java`:
```java
package code.with.vanilson.authentication.application;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * AccountUpdateResponse — result of PATCH /api/v1/auth/account/me.
 * tokens is non-null ONLY when the email changed: the JWT subject is the email, so the
 * caller's current access token died with the old address and it must adopt this pair.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AccountUpdateResponse(
        AccountResponse account,
        AuthResponse tokens
) {}
```

`InvalidAccountPasswordException.java`:
```java
package code.with.vanilson.authentication.exception;

import org.springframework.http.HttpStatus;

/** HTTP 400 — the re-auth password on an account-settings operation is wrong or missing. */
public class InvalidAccountPasswordException extends AuthBaseException {
    public InvalidAccountPasswordException(String resolvedMessage, String messageKey) {
        super(resolvedMessage, HttpStatus.BAD_REQUEST, messageKey);
    }
}
```

Append to `messages.properties` (after the `auth.reset.*` block, line ~30):
```properties
auth.account.email.taken=That email address is already in use by another account.
auth.account.password.invalid=The password you entered is incorrect.
auth.account.password.mismatch=The passwords do not match.
```

Add handler to `AuthGlobalExceptionHandler` (after `handleInvalidPasswordResetToken`, line ~113):
```java
    @ExceptionHandler(InvalidAccountPasswordException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidAccountPassword(
            InvalidAccountPasswordException ex, WebRequest req) {
        log.warn("[AuthHandler] Invalid account password: key=[{}]", ex.getMessageKey());
        return build(ex.getHttpStatus(), ex.getMessage(), ex.getMessageKey(), req);
    }
```

Add the compile stub to `CustomerProvisioning.java` (full async body replaces it in Task 3):
```java
    /** Task 3 implements the real async fail-open sync — stub keeps Task 1 compiling. */
    public void syncCustomerProfile(User user) {
        // implemented in Task 3 (async internal PUT to customer-service)
    }
```

`AccountService.java` (getAccount + updateAccount only; changePassword/deleteAccount come in Task 2):
```java
package code.with.vanilson.authentication.application;

import code.with.vanilson.authentication.domain.User;
import code.with.vanilson.authentication.exception.AuthUserNotFoundException;
import code.with.vanilson.authentication.exception.InvalidAccountPasswordException;
import code.with.vanilson.authentication.exception.UserAlreadyExistsException;
import code.with.vanilson.authentication.infrastructure.JwtService;
import code.with.vanilson.authentication.infrastructure.TokenRepository;
import code.with.vanilson.authentication.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * AccountService — Application Layer.
 * <p>
 * Self-service account management for the AUTHENTICATED user only (never another user's id —
 * the controller always passes the principal's own id). The JWT subject is the email, so an
 * email change revokes every session and returns a fresh token pair; name-only edits do not.
 * Identity changes are pushed to customer-service asynchronously and fail-open via
 * {@link CustomerProvisioning} — auth never blocks on customer-service.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private final UserRepository       userRepository;
    private final TokenRepository      tokenRepository;
    private final JwtService           jwtService;
    private final PasswordEncoder      passwordEncoder;
    private final RefreshTokenService  refreshTokenService;
    private final CustomerProvisioning customerProvisioning;
    private final MessageSource        messageSource;

    @Transactional(readOnly = true)
    public AccountResponse getAccount(Long userId) {
        return AccountResponse.from(findUser(userId));
    }

    @Transactional
    public AccountUpdateResponse updateAccount(Long userId, UpdateAccountRequest request) {
        User user = findUser(userId);
        boolean emailChanged = !user.getEmail().equalsIgnoreCase(request.email());

        if (emailChanged) {
            requireValidPassword(request.currentPassword(), user);
            if (userRepository.existsByEmail(request.email())) {
                throw new UserAlreadyExistsException(
                        msg("auth.account.email.taken"), "auth.account.email.taken");
            }
            user.setEmail(request.email());
        }
        user.setFirstname(request.firstname());
        user.setLastname(request.lastname());
        User saved = userRepository.save(user);

        // Fire-and-forget — keeps the customer profile's display identity in sync.
        customerProvisioning.syncCustomerProfile(saved);

        if (!emailChanged) {
            return new AccountUpdateResponse(AccountResponse.from(saved), null);
        }

        // The old email is dead as a JWT subject — kill every session, mint a fresh pair.
        tokenRepository.revokeAllUserTokens(saved.getId());
        String accessJwt  = jwtService.generateAccessToken(saved);
        String refreshJwt = jwtService.generateRefreshToken(saved);
        refreshTokenService.persistTokenPair(saved, accessJwt, refreshJwt);
        log.info("[AccountService] Email changed for userId=[{}] — sessions rotated", saved.getId());

        return new AccountUpdateResponse(AccountResponse.from(saved),
                AuthResponse.of(accessJwt, refreshJwt, String.valueOf(saved.getId()),
                        saved.getEmail(), saved.getRole().name(), saved.getTenantId()));
    }

    // -------------------------------------------------------
    // Helpers
    // -------------------------------------------------------

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AuthUserNotFoundException(
                        "User " + userId + " not found", "auth.user.not.found"));
    }

    private void requireValidPassword(String rawPassword, User user) {
        if (!StringUtils.hasText(rawPassword)
                || !passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new InvalidAccountPasswordException(
                    msg("auth.account.password.invalid"), "auth.account.password.invalid");
        }
    }

    private String msg(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -pl authentication-service -Dtest=AccountServiceTest`
Expected: all GetAccount + UpdateAccount tests PASS.

---

### Task 2: auth-service — `AccountService.changePassword` + `deleteAccount` (unit tests)

**Files:**
- Modify: `authentication-service/src/main/java/code/with/vanilson/authentication/application/AccountService.java`
- Modify: `authentication-service/src/main/java/code/with/vanilson/authentication/application/CustomerProvisioning.java` (add `deleteCustomerProfile` stub)
- Test: `authentication-service/src/test/java/code/with/vanilson/authentication/application/AccountServiceTest.java` (add two `@Nested` groups)

**Interfaces:**
- Consumes: same deps as Task 1; `CustomerProvisioning.deleteCustomerProfile(Long)` (stub now, real body Task 3).
- Produces: `AccountService.changePassword(Long, ChangePasswordRequest): AuthResponse`; `AccountService.deleteAccount(Long, String rawPassword): void`. Tombstone email format: `deleted-<id>@removed.local`.

- [ ] **Step 1: Add the failing tests to `AccountServiceTest`**

```java
    @Nested @DisplayName("ChangePassword")
    class ChangePassword {
        @Test
        void valid_change_rehashes_revokes_sessions_and_returns_fresh_pair() {
            when(userRepository.findById(7L)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("OldPass123!", "$2a$10$hash")).thenReturn(true);
            when(passwordEncoder.encode("NewPass123!")).thenReturn("$2a$10$newhash");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
            when(jwtService.generateAccessToken(any(User.class))).thenReturn("access2");
            when(jwtService.generateRefreshToken(any(User.class))).thenReturn("refresh2");

            AuthResponse res = service.changePassword(7L,
                    new ChangePasswordRequest("OldPass123!", "NewPass123!", "NewPass123!"));

            assertThat(res.accessToken()).isEqualTo("access2");
            assertThat(user.getPassword()).isEqualTo("$2a$10$newhash");
            verify(tokenRepository).revokeAllUserTokens(7L);
            verify(refreshTokenService).persistTokenPair(any(User.class), eq("access2"), eq("refresh2"));
        }

        @Test
        void wrong_current_password_is_rejected() {
            when(userRepository.findById(7L)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrong", "$2a$10$hash")).thenReturn(false);

            assertThatThrownBy(() -> service.changePassword(7L,
                    new ChangePasswordRequest("wrong", "NewPass123!", "NewPass123!")))
                    .isInstanceOf(InvalidAccountPasswordException.class);
            verify(userRepository, never()).save(any());
        }

        @Test
        void mismatched_confirmation_is_rejected_without_touching_the_user() {
            when(userRepository.findById(7L)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("OldPass123!", "$2a$10$hash")).thenReturn(true);

            assertThatThrownBy(() -> service.changePassword(7L,
                    new ChangePasswordRequest("OldPass123!", "NewPass123!", "Different!")))
                    .isInstanceOf(InvalidAccountPasswordException.class)
                    .hasMessageContaining("auth.account.password.mismatch");
            verify(userRepository, never()).save(any());
        }
    }

    @Nested @DisplayName("DeleteAccount")
    class DeleteAccount {
        @Test
        void soft_deletes_anonymizes_revokes_and_propagates() {
            when(userRepository.findById(7L)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("Secret123!", "$2a$10$hash")).thenReturn(true);
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            service.deleteAccount(7L, "Secret123!");

            assertThat(user.isAccountEnabled()).isFalse();
            assertThat(user.getEmail()).isEqualTo("deleted-7@removed.local");
            assertThat(user.getFirstname()).isEqualTo("Deleted");
            assertThat(user.getLastname()).isEqualTo("User");
            verify(tokenRepository).revokeAllUserTokens(7L);
            verify(customerProvisioning).deleteCustomerProfile(7L);
        }

        @Test
        void wrong_password_blocks_deletion() {
            when(userRepository.findById(7L)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrong", "$2a$10$hash")).thenReturn(false);

            assertThatThrownBy(() -> service.deleteAccount(7L, "wrong"))
                    .isInstanceOf(InvalidAccountPasswordException.class);
            assertThat(user.isAccountEnabled()).isTrue();
            verify(userRepository, never()).save(any());
        }
    }
```

- [ ] **Step 2: Run to verify the new tests fail**

Run: `mvn test -pl authentication-service -Dtest=AccountServiceTest`
Expected: COMPILATION ERROR (`changePassword` / `deleteAccount` missing).

- [ ] **Step 3: Implement both methods in `AccountService` + the provisioning stub**

Append to `AccountService`:
```java
    @Transactional
    public AuthResponse changePassword(Long userId, ChangePasswordRequest request) {
        User user = findUser(userId);
        requireValidPassword(request.currentPassword(), user);
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new InvalidAccountPasswordException(
                    msg("auth.account.password.mismatch"), "auth.account.password.mismatch");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        User saved = userRepository.save(user);

        // Same rule as the reset flow: a password change invalidates every outstanding session.
        tokenRepository.revokeAllUserTokens(saved.getId());
        String accessJwt  = jwtService.generateAccessToken(saved);
        String refreshJwt = jwtService.generateRefreshToken(saved);
        refreshTokenService.persistTokenPair(saved, accessJwt, refreshJwt);
        log.info("[AccountService] Password changed for userId=[{}] — sessions rotated", saved.getId());

        return AuthResponse.of(accessJwt, refreshJwt, String.valueOf(saved.getId()),
                saved.getEmail(), saved.getRole().name(), saved.getTenantId());
    }

    @Transactional
    public void deleteAccount(Long userId, String rawPassword) {
        User user = findUser(userId);
        requireValidPassword(rawPassword, user);

        // Soft delete + anonymize (GDPR-style): orders/payments keep their history, the real
        // email is freed for future re-registration, and login stays a generic 401.
        user.setAccountEnabled(false);
        user.setEmail("deleted-" + user.getId() + "@removed.local");
        user.setFirstname("Deleted");
        user.setLastname("User");
        userRepository.save(user);

        tokenRepository.revokeAllUserTokens(user.getId());
        customerProvisioning.deleteCustomerProfile(user.getId());
        log.info("[AccountService] Account soft-deleted for userId=[{}]", user.getId());
    }
```

Add to `CustomerProvisioning`:
```java
    /** Task 3 implements the real async fail-open delete — stub keeps Task 2 compiling. */
    public void deleteCustomerProfile(Long userId) {
        // implemented in Task 3 (async internal DELETE to customer-service)
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -pl authentication-service -Dtest=AccountServiceTest`
Expected: ALL nested groups PASS.

---

### Task 3: auth-service — Feign internal update/delete + real async propagation (unit tests)

**Files:**
- Modify: `authentication-service/src/main/java/code/with/vanilson/authentication/infrastructure/CustomerRegistrationClient.java`
- Modify: `authentication-service/src/main/java/code/with/vanilson/authentication/application/CustomerProvisioning.java` (replace both stubs)
- Test: `authentication-service/src/test/java/code/with/vanilson/authentication/application/CustomerProvisioningTest.java` (create)

**Interfaces:**
- Consumes: existing `CustomerRegistrationClient.CustomerRegistrationRequest(String customerId, String firstname, String lastname, String email)`; `AsyncConfig.AUTH_SIDE_EFFECTS_EXECUTOR` constant.
- Produces: `CustomerRegistrationClient.updateCustomer(String, CustomerRegistrationRequest)`, `CustomerRegistrationClient.deleteCustomer(String)`; real `CustomerProvisioning.syncCustomerProfile(User)` / `deleteCustomerProfile(Long)` — `@Async`, fail-open (exceptions swallowed + WARN-logged). Task 6 implements the matching customer-service endpoints `PUT/DELETE /api/v1/customers/internal/{id}`.

- [ ] **Step 1: Write the failing unit tests**

```java
package code.with.vanilson.authentication.application;

import code.with.vanilson.authentication.domain.Role;
import code.with.vanilson.authentication.domain.User;
import code.with.vanilson.authentication.infrastructure.CustomerRegistrationClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("CustomerProvisioning")
class CustomerProvisioningTest {

    @Mock CustomerRegistrationClient client;
    @InjectMocks CustomerProvisioning provisioning;

    private final User user = User.builder()
            .id(9L).firstname("Rui").lastname("Costa").email("rui@x.com")
            .role(Role.USER).tenantId("default").password("h").build();

    @Nested @DisplayName("syncCustomerProfile")
    class Sync {
        @Test
        void forwards_identity_to_internal_update() {
            provisioning.syncCustomerProfile(user);
            verify(client).updateCustomer(eq("9"),
                    any(CustomerRegistrationClient.CustomerRegistrationRequest.class));
        }

        @Test
        void is_fail_open_when_customer_service_is_down() {
            doThrow(new RuntimeException("connection refused"))
                    .when(client).updateCustomer(any(), any());
            assertThatCode(() -> provisioning.syncCustomerProfile(user))
                    .doesNotThrowAnyException();
        }
    }

    @Nested @DisplayName("deleteCustomerProfile")
    class Delete {
        @Test
        void forwards_delete_to_internal_endpoint() {
            provisioning.deleteCustomerProfile(9L);
            verify(client).deleteCustomer("9");
        }

        @Test
        void is_fail_open_when_customer_service_is_down() {
            doThrow(new RuntimeException("boom")).when(client).deleteCustomer("9");
            assertThatCode(() -> provisioning.deleteCustomerProfile(9L))
                    .doesNotThrowAnyException();
        }
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `mvn test -pl authentication-service -Dtest=CustomerProvisioningTest`
Expected: FAIL — `updateCustomer`/`deleteCustomer` don't exist on the client; stubs do nothing.

- [ ] **Step 3: Implement client methods + real async bodies**

Add to `CustomerRegistrationClient` (same `@CircuitBreaker` name):
```java
    @CircuitBreaker(name = "customer-registration")
    @PutMapping("/internal/{customerId}")
    void updateCustomer(@PathVariable("customerId") String customerId,
                        @RequestBody CustomerRegistrationRequest request);

    @CircuitBreaker(name = "customer-registration")
    @DeleteMapping("/internal/{customerId}")
    void deleteCustomer(@PathVariable("customerId") String customerId);
```
(new imports: `org.springframework.web.bind.annotation.PutMapping`, `DeleteMapping`, `PathVariable`.)

Replace both stubs in `CustomerProvisioning`:
```java
    /**
     * Pushes the user's current display identity (name/email) to the customer profile,
     * OFF the request thread. Fail-open + idempotent: the internal endpoint no-ops when
     * the profile doesn't exist, and a missed sync self-heals on the next settings save.
     */
    @Async(AsyncConfig.AUTH_SIDE_EFFECTS_EXECUTOR)
    public void syncCustomerProfile(User user) {
        try {
            customerRegistrationClient.updateCustomer(
                    String.valueOf(user.getId()),
                    new CustomerRegistrationClient.CustomerRegistrationRequest(
                            String.valueOf(user.getId()),
                            user.getFirstname(),
                            user.getLastname(),
                            user.getEmail()));
            log.info("[CustomerProvisioning] Synced customer profile for userId=[{}]", user.getId());
        } catch (Exception ex) {
            log.warn("[CustomerProvisioning] Customer profile sync failed for userId=[{}]: {}",
                    user.getId(), ex.getMessage());
        }
    }

    /**
     * Deletes the customer profile after an account soft-delete, OFF the request thread.
     * Fail-open: the auth-side delete already succeeded; an orphaned profile is harmless
     * and the internal endpoint is idempotent (204 even when already gone).
     */
    @Async(AsyncConfig.AUTH_SIDE_EFFECTS_EXECUTOR)
    public void deleteCustomerProfile(Long userId) {
        try {
            customerRegistrationClient.deleteCustomer(String.valueOf(userId));
            log.info("[CustomerProvisioning] Deleted customer profile for userId=[{}]", userId);
        } catch (Exception ex) {
            log.warn("[CustomerProvisioning] Customer profile delete failed for userId=[{}]: {}",
                    userId, ex.getMessage());
        }
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -pl authentication-service -Dtest="CustomerProvisioningTest,AccountServiceTest"`
Expected: PASS (async annotation is inert in plain Mockito tests — methods run synchronously, which is what the verify() calls rely on).

---

### Task 4: auth-service — `AccountController` + WebMvc slice tests

**Files:**
- Create: `authentication-service/src/main/java/code/with/vanilson/authentication/presentation/AccountController.java`
- Test: `authentication-service/src/test/java/code/with/vanilson/authentication/controller/AccountControllerTest.java`

**Interfaces:**
- Consumes: `AccountService` (Tasks 1–2 signatures), `UserDetailsAdapter` principal (`actor.getUser().getId()`).
- Produces: HTTP surface `GET/PATCH /api/v1/auth/account/me`, `POST /api/v1/auth/account/change-password`, `DELETE /api/v1/auth/account/me`. No SecurityConfig change — `/api/v1/auth/account/**` falls under `.anyRequest().authenticated()`.

- [ ] **Step 1: Write the failing slice tests**

Model: `SellerProfileControllerTest` (same `@WebMvcTest` + `@Import(SecurityConfig.class)` + mocked `JwtAuthFilter` pass-through + `with(user(principal))`).

```java
package code.with.vanilson.authentication.controller;

import code.with.vanilson.authentication.application.AccountResponse;
import code.with.vanilson.authentication.application.AccountService;
import code.with.vanilson.authentication.application.AccountUpdateResponse;
import code.with.vanilson.authentication.application.AuthResponse;
import code.with.vanilson.authentication.application.ChangePasswordRequest;
import code.with.vanilson.authentication.application.UpdateAccountRequest;
import code.with.vanilson.authentication.config.JwtAuthFilter;
import code.with.vanilson.authentication.config.SecurityConfig;
import code.with.vanilson.authentication.domain.Role;
import code.with.vanilson.authentication.domain.User;
import code.with.vanilson.authentication.domain.UserDetailsAdapter;
import code.with.vanilson.authentication.exception.InvalidAccountPasswordException;
import code.with.vanilson.authentication.exception.UserAlreadyExistsException;
import code.with.vanilson.authentication.infrastructure.JwtService;
import code.with.vanilson.authentication.infrastructure.TokenRepository;
import code.with.vanilson.authentication.infrastructure.UserDetailsServiceImpl;
import code.with.vanilson.authentication.presentation.AccountController;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
@DisplayName("AccountController - WebMvc slice tests")
class AccountControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean AccountService         service;
    @MockBean JwtService             jwtService;
    @MockBean TokenRepository        tokenRepository;
    @MockBean UserDetailsServiceImpl userDetailsService;
    @MockBean JwtAuthFilter          jwtAuthFilter;

    private static final String BASE = "/api/v1/auth/account";

    private UserDetailsAdapter userPrincipal;
    private UserDetailsAdapter sellerPrincipal;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(inv -> {
            FilterChain chain = inv.getArgument(2);
            chain.doFilter(
                    inv.<ServletRequest>getArgument(0),
                    inv.<ServletResponse>getArgument(1));
            return null;
        }).when(jwtAuthFilter).doFilter(any(), any(), any());

        userPrincipal = new UserDetailsAdapter(
                User.builder().id(7L).email("ana@x.com").role(Role.USER)
                        .tenantId("default").firstname("Ana").lastname("Silva")
                        .password("hashed").accountEnabled(true).build());
        sellerPrincipal = new UserDetailsAdapter(
                User.builder().id(8L).email("seller@x.com").role(Role.SELLER)
                        .tenantId("default").firstname("Sam").lastname("Seller")
                        .password("hashed").accountEnabled(true).build());
    }

    private AccountResponse account() {
        return new AccountResponse(7L, "Ana", "Silva", "ana@x.com", "USER",
                LocalDateTime.of(2026, 1, 1, 0, 0));
    }

    @Nested @DisplayName("GET /me")
    class GetMe {
        @Test
        void returns_own_account() throws Exception {
            when(service.getAccount(7L)).thenReturn(account());
            mockMvc.perform(get(BASE + "/me").with(user(userPrincipal)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email", is("ana@x.com")))
                    .andExpect(jsonPath("$.role", is("USER")));
        }

        @Test
        void anonymous_gets_401() throws Exception {
            mockMvc.perform(get(BASE + "/me")).andExpect(status().isUnauthorized());
        }
    }

    @Nested @DisplayName("PATCH /me")
    class PatchMe {
        @Test
        void name_only_update_returns_account_without_tokens() throws Exception {
            when(service.updateAccount(eq(7L), any(UpdateAccountRequest.class)))
                    .thenReturn(new AccountUpdateResponse(account(), null));
            mockMvc.perform(patch(BASE + "/me").with(user(userPrincipal))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"firstname\":\"Ana\",\"lastname\":\"Silva\",\"email\":\"ana@x.com\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.account.email", is("ana@x.com")))
                    .andExpect(jsonPath("$.tokens").doesNotExist());
        }

        @Test
        void email_change_returns_fresh_token_pair() throws Exception {
            AuthResponse tokens = AuthResponse.of("a2", "r2", "7", "new@x.com", "USER", "default");
            when(service.updateAccount(eq(7L), any(UpdateAccountRequest.class)))
                    .thenReturn(new AccountUpdateResponse(
                            new AccountResponse(7L, "Ana", "Silva", "new@x.com", "USER",
                                    LocalDateTime.of(2026, 1, 1, 0, 0)), tokens));
            mockMvc.perform(patch(BASE + "/me").with(user(userPrincipal))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"firstname\":\"Ana\",\"lastname\":\"Silva\","
                                    + "\"email\":\"new@x.com\",\"currentPassword\":\"Secret123!\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tokens.accessToken", is("a2")))
                    .andExpect(jsonPath("$.account.email", is("new@x.com")));
        }

        @Test
        void taken_email_maps_to_409() throws Exception {
            when(service.updateAccount(eq(7L), any(UpdateAccountRequest.class)))
                    .thenThrow(new UserAlreadyExistsException(
                            "That email address is already in use by another account.",
                            "auth.account.email.taken"));
            mockMvc.perform(patch(BASE + "/me").with(user(userPrincipal))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"firstname\":\"Ana\",\"lastname\":\"Silva\","
                                    + "\"email\":\"taken@x.com\",\"currentPassword\":\"Secret123!\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode", is("auth.account.email.taken")));
        }

        @Test
        void wrong_password_maps_to_400() throws Exception {
            when(service.updateAccount(eq(7L), any(UpdateAccountRequest.class)))
                    .thenThrow(new InvalidAccountPasswordException(
                            "The password you entered is incorrect.", "auth.account.password.invalid"));
            mockMvc.perform(patch(BASE + "/me").with(user(userPrincipal))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"firstname\":\"Ana\",\"lastname\":\"Silva\","
                                    + "\"email\":\"new@x.com\",\"currentPassword\":\"wrong\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode", is("auth.account.password.invalid")));
        }

        @Test
        void blank_firstname_fails_bean_validation() throws Exception {
            mockMvc.perform(patch(BASE + "/me").with(user(userPrincipal))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"firstname\":\"\",\"lastname\":\"Silva\",\"email\":\"ana@x.com\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode", is("auth.validation.failed")));
        }
    }

    @Nested @DisplayName("POST /change-password")
    class ChangePassword {
        @Test
        void valid_change_returns_fresh_pair() throws Exception {
            when(service.changePassword(eq(7L), any(ChangePasswordRequest.class)))
                    .thenReturn(AuthResponse.of("a3", "r3", "7", "ana@x.com", "USER", "default"));
            mockMvc.perform(post(BASE + "/change-password").with(user(userPrincipal))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"currentPassword\":\"OldPass123!\","
                                    + "\"newPassword\":\"NewPass123!\",\"confirmPassword\":\"NewPass123!\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken", is("a3")));
        }

        @Test
        void short_new_password_fails_bean_validation() throws Exception {
            mockMvc.perform(post(BASE + "/change-password").with(user(userPrincipal))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"currentPassword\":\"OldPass123!\","
                                    + "\"newPassword\":\"short\",\"confirmPassword\":\"short\"}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested @DisplayName("DELETE /me")
    class DeleteMe {
        @Test
        void user_role_deletes_own_account_204() throws Exception {
            mockMvc.perform(delete(BASE + "/me").with(user(userPrincipal))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"password\":\"Secret123!\"}"))
                    .andExpect(status().isNoContent());
            verify(service).deleteAccount(7L, "Secret123!");
        }

        @Test
        void seller_role_gets_403() throws Exception {
            mockMvc.perform(delete(BASE + "/me").with(user(sellerPrincipal))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"password\":\"Secret123!\"}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void anonymous_gets_401() throws Exception {
            mockMvc.perform(delete(BASE + "/me")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"password\":\"x\"}"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `mvn test -pl authentication-service -Dtest=AccountControllerTest`
Expected: COMPILATION ERROR (`AccountController` missing).

- [ ] **Step 3: Implement `AccountController`**

```java
package code.with.vanilson.authentication.presentation;

import code.with.vanilson.authentication.application.AccountResponse;
import code.with.vanilson.authentication.application.AccountService;
import code.with.vanilson.authentication.application.AccountUpdateResponse;
import code.with.vanilson.authentication.application.AuthResponse;
import code.with.vanilson.authentication.application.ChangePasswordRequest;
import code.with.vanilson.authentication.application.DeleteAccountRequest;
import code.with.vanilson.authentication.application.UpdateAccountRequest;
import code.with.vanilson.authentication.domain.UserDetailsAdapter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AccountController — Presentation Layer.
 * <p>
 * Self-service account settings for the AUTHENTICATED user (always the principal's own id —
 * there is no path id to tamper with). Delete is USER-only: seller/admin self-deletion has
 * platform implications (live products / lockout) and is deliberately excluded here.
 * </p>
 */
@RestController
@RequestMapping("/api/v1/auth/account")
@RequiredArgsConstructor
@Tag(name = "Account Settings", description = "Self-service identity, password, and account deletion")
public class AccountController {

    private final AccountService service;

    @Operation(summary = "Get my account (login identity)")
    @ApiResponse(responseCode = "200", description = "Account returned")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me")
    public ResponseEntity<AccountResponse> me(@AuthenticationPrincipal UserDetailsAdapter actor) {
        return ResponseEntity.ok(service.getAccount(actor.getUser().getId()));
    }

    @Operation(summary = "Update my name/email",
               description = "currentPassword is required when the email changes. An email change "
                       + "returns a fresh token pair (the JWT subject is the email).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Account updated"),
        @ApiResponse(responseCode = "400", description = "Wrong/missing password or invalid payload"),
        @ApiResponse(responseCode = "409", description = "Email already in use")
    })
    @PreAuthorize("isAuthenticated()")
    @PatchMapping("/me")
    public ResponseEntity<AccountUpdateResponse> update(
            @AuthenticationPrincipal UserDetailsAdapter actor,
            @RequestBody @Valid UpdateAccountRequest request) {
        return ResponseEntity.ok(service.updateAccount(actor.getUser().getId(), request));
    }

    @Operation(summary = "Change my password",
               description = "Revokes every session and returns a fresh token pair.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Password changed, new tokens returned"),
        @ApiResponse(responseCode = "400", description = "Wrong current password or mismatch")
    })
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/change-password")
    public ResponseEntity<AuthResponse> changePassword(
            @AuthenticationPrincipal UserDetailsAdapter actor,
            @RequestBody @Valid ChangePasswordRequest request) {
        return ResponseEntity.ok(service.changePassword(actor.getUser().getId(), request));
    }

    @Operation(summary = "Delete my account (customers only)",
               description = "Soft delete + anonymize; frees the email for re-registration. "
                       + "SELLER/ADMIN receive 403 — their deletion is an admin operation.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Account deleted"),
        @ApiResponse(responseCode = "400", description = "Wrong password"),
        @ApiResponse(responseCode = "403", description = "Not a USER-role account")
    })
    @PreAuthorize("hasRole('USER')")
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMe(
            @AuthenticationPrincipal UserDetailsAdapter actor,
            @RequestBody @Valid DeleteAccountRequest request) {
        service.deleteAccount(actor.getUser().getId(), request.password());
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -pl authentication-service -Dtest=AccountControllerTest`
Expected: ALL PASS (including 403 for seller on DELETE and 401 anonymous).

---

### Task 5: auth-service — BDD feature + step definitions, full module suite green

**Files:**
- Create: `authentication-service/src/test/resources/features/account-settings.feature`
- Create: `authentication-service/src/test/java/code/with/vanilson/authentication/bdd/steps/AccountStepDefinitions.java`

**Interfaces:**
- Consumes: running app via RestAssured (same mechanism as `AuthStepDefinitions`: no `@Component`, `@Autowired Environment`, port from `local.server.port` in `@Before`). Endpoints from Task 4.
- Produces: BDD proof of the end-to-end rules. The existing `CucumberIntegrationTest` runner auto-discovers the new feature + glue (same package).

- [ ] **Step 1: Write the feature file**

```gherkin
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

  Scenario: Delete my account, then re-register with the same email
    Given a registered user "settings.delete@bdd.com" with password "Passw0rd!1"
    When the user deletes their account with password "Passw0rd!1"
    Then login with the old email "settings.delete@bdd.com" and password "Passw0rd!1" fails with 401
    And registering again with email "settings.delete@bdd.com" and password "Passw0rd!1" succeeds
```

- [ ] **Step 2: Write the step definitions**

Same conventions as `AuthStepDefinitions` (no `@Component`, per-scenario state, `uniqueEmail` seed cache so Given/When/Then share the generated address):

```java
package code.with.vanilson.authentication.bdd.steps;

import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * AccountStepDefinitions — steps for account-settings.feature.
 * Same lifecycle rules as AuthStepDefinitions: NOT @Component (cucumber-spring autowires
 * a fresh instance per scenario); port resolved lazily in @Before.
 */
public class AccountStepDefinitions {

    private static final AtomicLong SEQ  = new AtomicLong(System.nanoTime());
    private static final String     BASE = "/api/v1/auth";

    @Autowired
    private Environment environment;

    private Response            lastResponse;
    private String              accessToken;
    private Map<String, String> emailCache;

    @Before
    public void setUp() {
        int port = Integer.parseInt(environment.getProperty("local.server.port", "8080"));
        RestAssured.baseURI = "http://localhost";
        RestAssured.port    = port;
        lastResponse = null;
        accessToken  = null;
        emailCache   = new HashMap<>();
    }

    private String uniqueEmail(String seed) {
        return emailCache.computeIfAbsent(
                seed, s -> s.replace("@", "_" + SEQ.incrementAndGet() + "@"));
    }

    private Response register(String email, String password) {
        return given().contentType(ContentType.JSON)
                .body(String.format(
                        "{\"firstname\":\"Acc\",\"lastname\":\"Bdd\","
                        + "\"email\":\"%s\",\"password\":\"%s\"}", email, password))
                .post(BASE + "/register").then().extract().response();
    }

    private Response login(String email, String password) {
        return given().contentType(ContentType.JSON)
                .body(String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, password))
                .post(BASE + "/login").then().extract().response();
    }

    // ------------------------------------------------------------------

    @Given("a registered user {string} with password {string}")
    public void aRegisteredUser(String emailSeed, String password) {
        Response r = register(uniqueEmail(emailSeed), password);
        assertThat(r.statusCode()).isEqualTo(201);
        accessToken = r.jsonPath().getString("accessToken");
    }

    @When("the user updates their name to {string} {string}")
    public void updatesName(String firstname, String lastname) {
        String email = lastResponseEmailOrRegistered();
        lastResponse = given().contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken)
                .body(String.format(
                        "{\"firstname\":\"%s\",\"lastname\":\"%s\",\"email\":\"%s\"}",
                        firstname, lastname, email))
                .patch(BASE + "/account/me").then().extract().response();
    }

    @When("the user changes their email to {string} using password {string}")
    public void changesEmail(String newEmailSeed, String password) {
        String current = lastResponseEmailOrRegistered();
        lastResponse = given().contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken)
                .body(String.format(
                        "{\"firstname\":\"Acc\",\"lastname\":\"Bdd\","
                        + "\"email\":\"%s\",\"currentPassword\":\"%s\"}",
                        uniqueEmail(newEmailSeed), password))
                .patch(BASE + "/account/me").then().extract().response();
        String rotated = lastResponse.jsonPath().getString("tokens.accessToken");
        if (rotated != null) {
            accessToken = rotated;
        }
        // 'current' intentionally unused further — kept for readability of the flow.
        assertThat(current).isNotBlank();
    }

    @When("the user changes their password from {string} to {string}")
    public void changesPassword(String oldPw, String newPw) {
        lastResponse = given().contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken)
                .body(String.format(
                        "{\"currentPassword\":\"%s\",\"newPassword\":\"%s\",\"confirmPassword\":\"%s\"}",
                        oldPw, newPw, newPw))
                .post(BASE + "/account/change-password").then().extract().response();
        assertThat(lastResponse.statusCode()).isEqualTo(200);
        accessToken = lastResponse.jsonPath().getString("accessToken");
    }

    @When("the user deletes their account with password {string}")
    public void deletesAccount(String password) {
        lastResponse = given().contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken)
                .body(String.format("{\"password\":\"%s\"}", password))
                .delete(BASE + "/account/me").then().extract().response();
        assertThat(lastResponse.statusCode()).isEqualTo(204);
    }

    // ------------------------------------------------------------------

    @Then("the account response shows firstname {string} and no new tokens")
    public void accountShowsFirstname(String firstname) {
        assertThat(lastResponse.statusCode()).isEqualTo(200);
        assertThat(lastResponse.jsonPath().getString("account.firstname")).isEqualTo(firstname);
        assertThat(lastResponse.jsonPath().getString("tokens")).isNull();
    }

    @Then("the response contains a fresh token pair")
    public void responseContainsFreshPair() {
        assertThat(lastResponse.statusCode()).isEqualTo(200);
        assertThat(lastResponse.jsonPath().getString("tokens.accessToken")).isNotBlank();
        assertThat(lastResponse.jsonPath().getString("tokens.refreshToken")).isNotBlank();
    }

    @Then("the request fails with {int} and error code {string}")
    public void requestFailsWith(int status, String errorCode) {
        assertThat(lastResponse.statusCode()).isEqualTo(status);
        assertThat(lastResponse.jsonPath().getString("errorCode")).isEqualTo(errorCode);
    }

    @And("login with the old email {string} and password {string} fails with 401")
    public void oldLoginFails(String emailSeed, String password) {
        assertThat(login(uniqueEmail(emailSeed), password).statusCode()).isEqualTo(401);
    }

    @And("login with the new email {string} and password {string} succeeds")
    public void newLoginSucceeds(String emailSeed, String password) {
        assertThat(login(uniqueEmail(emailSeed), password).statusCode()).isEqualTo(200);
    }

    @And("registering again with email {string} and password {string} succeeds")
    public void reRegisterSucceeds(String emailSeed, String password) {
        // The seed maps to the SAME generated address the deleted account used —
        // proving the tombstone freed the email.
        assertThat(register(uniqueEmail(emailSeed), password).statusCode()).isEqualTo(201);
    }

    /** The email the current scenario registered with (single-user scenarios). */
    private String lastResponseEmailOrRegistered() {
        return emailCache.values().iterator().next();
    }
}
```

- [ ] **Step 3: Run the BDD suite**

Run: `mvn test -pl authentication-service -Dtest=CucumberIntegrationTest`
Expected: all existing auth.feature scenarios + the 5 new ones PASS.

- [ ] **Step 4: Run the FULL auth-service suite**

Run: `mvn test -pl authentication-service`
Expected: BUILD SUCCESS, zero failures (was 135 tests before this feature; now ~155+).

---

### Task 6: customer-service — idempotent internal PUT/DELETE (unit + slice + BDD)

**Files:**
- Modify: `customer-service/src/main/java/code/with/vanilson/customerservice/CustomerController.java` (2 endpoints)
- Modify: `customer-service/src/main/java/code/with/vanilson/customerservice/CustomerService.java` (2 methods)
- Test: `customer-service/src/test/java/.../CustomerServiceTest.java` (add `@Nested` groups — locate the existing file with Glob `customer-service/src/test/java/**/CustomerServiceTest.java` and follow its mock setup)
- Test: the existing controller slice test class for CustomerController (locate via Glob `customer-service/src/test/java/**/*ControllerTest.java`) — add internal-endpoint cases
- Test: existing BDD feature dir `customer-service/src/test/resources/features/` — add `internal-sync.feature` + steps following the existing step-def class conventions

**Interfaces:**
- Consumes: `CustomerRequest(String customerId, String firstname, String lastname, String email, Address address)` (existing), `CustomerRepository`, `customerMapper`, `customerProfileProducer`.
- Produces: `PUT /api/v1/customers/internal/{id}` → 204 always (merge names/email if profile exists, no-op otherwise); `DELETE /api/v1/customers/internal/{id}` → 204 always. Both are already `permitAll` via the existing `/api/v1/customers/internal/**` matcher in `CustomerSecurityConfig` — **no security change**. These are what Task 3's Feign methods call.

- [ ] **Step 1: Write the failing unit tests** (add to `CustomerServiceTest`, mirroring its existing mock fields):

```java
    @Nested
    @DisplayName("SyncInternal")
    class SyncInternal {
        @Test
        void merges_identity_when_profile_exists() {
            Customer existing = new Customer();
            existing.setCustomerId("9");
            existing.setFirstname("Old");
            existing.setLastname("Name");
            existing.setEmail("old@x.com");
            when(customerRepository.findById("9")).thenReturn(Optional.of(existing));
            when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

            customerService.syncCustomerInternal("9",
                    new CustomerRequest("9", "New", "Name", "new@x.com", null));

            assertThat(existing.getFirstname()).isEqualTo("New");
            assertThat(existing.getEmail()).isEqualTo("new@x.com");
            verify(customerRepository).save(existing);
        }

        @Test
        void is_a_noop_when_profile_missing() {
            when(customerRepository.findById("404")).thenReturn(Optional.empty());
            assertThatCode(() -> customerService.syncCustomerInternal("404",
                    new CustomerRequest("404", "X", "Y", "x@y.com", null)))
                    .doesNotThrowAnyException();
            verify(customerRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("DeleteInternal")
    class DeleteInternal {
        @Test
        void deletes_when_present() {
            Customer existing = new Customer();
            existing.setCustomerId("9");
            when(customerRepository.findById("9")).thenReturn(Optional.of(existing));

            customerService.deleteCustomerInternal("9");

            verify(customerRepository).deleteById("9");
        }

        @Test
        void is_a_noop_when_already_gone() {
            when(customerRepository.findById("404")).thenReturn(Optional.empty());
            assertThatCode(() -> customerService.deleteCustomerInternal("404"))
                    .doesNotThrowAnyException();
            verify(customerRepository, never()).deleteById(any());
        }
    }
```

(Adjust `Customer` construction to the entity's actual builder/setters — check the entity first; MongoDB document. If the existing test file builds Customers differently, follow that pattern.)

- [ ] **Step 2: Run to verify failure**

Run: `mvn test -pl customer-service -Dtest=CustomerServiceTest`
Expected: COMPILATION ERROR (methods missing).

- [ ] **Step 3: Implement service methods** (in `CustomerService`, after `deleteCustomer`, reusing the existing `mergeFields` + cache-evict conventions):

```java
    /**
     * Internal idempotent identity sync from auth-service (name/email only — address is
     * owned by the customer profile). No-op when the profile does not exist: provisioning
     * will create it on the user's next login, already carrying the new identity.
     */
    @Caching(evict = {
            @CacheEvict(value = CACHE_CUSTOMERS,     key = "#customerId"),
            @CacheEvict(value = CACHE_CUSTOMER_LIST, allEntries = true)
    })
    public void syncCustomerInternal(String customerId, CustomerRequest request) {
        customerRepository.findById(customerId).ifPresentOrElse(existing -> {
            mergeFields(existing, request);
            Customer saved = customerRepository.save(existing);
            customerProfileProducer.publishProfileEvent(saved, "UPDATED");
            log.info("[CustomerService] internal sync applied id=[{}]", customerId);
        }, () -> log.info("[CustomerService] internal sync no-op — id=[{}] not found", customerId));
    }

    /** Internal idempotent delete from auth-service account deletion. 204 semantics: always succeeds. */
    @Caching(evict = {
            @CacheEvict(value = CACHE_CUSTOMERS,     key = "#customerId"),
            @CacheEvict(value = CACHE_CUSTOMER_LIST, allEntries = true)
    })
    public void deleteCustomerInternal(String customerId) {
        customerRepository.findById(customerId).ifPresentOrElse(existing -> {
            customerRepository.deleteById(existing.getCustomerId());
            log.info("[CustomerService] internal delete applied id=[{}]", customerId);
        }, () -> log.info("[CustomerService] internal delete no-op — id=[{}] not found", customerId));
    }
```

Add controller endpoints (in `CustomerController`, after `registerInternal`):

```java
    /**
     * Internal idempotent identity sync (auth-service → customer profile). 204 even when the
     * profile is missing. Unauthenticated by design — /internal/** is not routed by the gateway.
     */
    @Operation(summary = "Internal: idempotent identity sync (service-to-service)")
    @PutMapping("/internal/{id}")
    public ResponseEntity<Void> syncInternal(
            @PathVariable String id,
            @RequestBody @Valid CustomerRequest request) {
        log.info("PUT /api/v1/customers/internal/{} — sync identity", id);
        customerService.syncCustomerInternal(id, request);
        return ResponseEntity.noContent().build();
    }

    /** Internal idempotent profile delete (auth-service account deletion). 204 always. */
    @Operation(summary = "Internal: idempotent profile delete (service-to-service)")
    @DeleteMapping("/internal/{id}")
    public ResponseEntity<Void> deleteInternal(@PathVariable String id) {
        log.info("DELETE /api/v1/customers/internal/{} — delete profile", id);
        customerService.deleteCustomerInternal(id);
        return ResponseEntity.noContent().build();
    }
```

- [ ] **Step 4: Run unit tests to verify they pass**

Run: `mvn test -pl customer-service -Dtest=CustomerServiceTest`
Expected: PASS.

- [ ] **Step 5: Add controller slice tests** — in the existing CustomerController test class (find with Glob; follow its existing security/mock setup exactly):
  - `PUT /api/v1/customers/internal/9` with a valid body → 204, service verified called.
  - `PUT /api/v1/customers/internal/404` (service no-op) → still 204.
  - `DELETE /api/v1/customers/internal/9` → 204, service verified.
  - Both endpoints reachable WITHOUT authentication (matches the `permitAll` matcher).

- [ ] **Step 6: Add BDD** — `customer-service/src/test/resources/features/internal-sync.feature`:

```gherkin
Feature: Internal identity sync and delete (service-to-service)

  Scenario: Auth-service pushes a new name and email to an existing profile
    Given a customer profile exists with id "sync-1" and email "before@bdd.com"
    When the internal sync updates id "sync-1" to name "After" "Sync" email "after@bdd.com"
    Then the profile "sync-1" has firstname "After" and email "after@bdd.com"

  Scenario: Sync for a missing profile is a silent no-op
    When the internal sync updates id "ghost-1" to name "No" "One" email "ghost@bdd.com"
    Then the internal call succeeded with status 204

  Scenario: Delete is idempotent
    Given a customer profile exists with id "del-1" and email "del@bdd.com"
    When the internal delete removes id "del-1"
    And the internal delete removes id "del-1"
    Then the internal call succeeded with status 204
```

Step definitions go next to the existing customer-service BDD steps (locate the package with Glob `customer-service/src/test/java/**/bdd/**`; follow the same RestAssured/MockMvc mechanism that module already uses — reuse its Given-a-profile helper if one exists).

- [ ] **Step 7: Run the FULL customer-service suite**

Run: `mvn test -pl customer-service`
Expected: BUILD SUCCESS, zero failures.

---

### Task 7: frontend — API types, authApi methods, MSW handlers, auth.store email setter

**Files:**
- Modify: `frontend/src/api/types.ts` (append account types after the Auth block)
- Modify: `frontend/src/api/auth.api.ts` (4 methods)
- Modify: `frontend/src/stores/auth.store.ts` (add `setEmail`)
- Modify: `frontend/src/test/mocks/handlers.ts` (4 handlers, following the existing forgot/reset handler style)

**Interfaces:**
- Consumes: backend contract from Tasks 1–4.
- Produces (used by Task 8):
```ts
authApi.getAccount(): Promise<AccountResponse>
authApi.updateAccount(data: UpdateAccountRequest): Promise<AccountUpdateResponse>
authApi.changePassword(data: ChangePasswordRequest): Promise<AuthResponse>
authApi.deleteAccount(password: string): Promise<void>
useAuthStore.getState().setEmail(email: string)
```

- [ ] **Step 1: Add types to `types.ts`** (after the `AuthResponse` interface):

```ts
export interface AccountResponse {
  id: number;
  firstname: string;
  lastname: string;
  email: string;
  role: Role;
  createdAt: string;
}

export interface UpdateAccountRequest {
  firstname: string;
  lastname: string;
  email: string;
  currentPassword?: string;
}

export interface AccountUpdateResponse {
  account: AccountResponse;
  // Present ONLY when the email changed — the caller must swap these into the auth store.
  tokens?: AuthResponse;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
  confirmPassword: string;
}
```

- [ ] **Step 2: Add methods to `auth.api.ts`** (after `resetPassword`, before the seller block):

```ts
  // Account settings — the LOGIN identity (auth-service), not the customer profile.
  getAccount: () =>
    apiClient.get<AccountResponse>('/auth/account/me').then((r) => r.data),

  updateAccount: (data: UpdateAccountRequest) =>
    apiClient.patch<AccountUpdateResponse>('/auth/account/me', data).then((r) => r.data),

  changePassword: (data: ChangePasswordRequest) =>
    apiClient
      .post<AuthResponse>('/auth/account/change-password', data)
      .then((r) => r.data),

  deleteAccount: (password: string) =>
    apiClient.delete('/auth/account/me', { data: { password } }).then(() => undefined),
```
Extend the type import at the top with `AccountResponse, AccountUpdateResponse, ChangePasswordRequest, UpdateAccountRequest`.

- [ ] **Step 3: Add `setEmail` to `auth.store.ts`**:

In the interface: `setEmail: (email: string) => void;`
In the implementation (after `setTokens`):
```ts
      setEmail: (email) => set({ email }),
```

- [ ] **Step 4: MSW handlers** — open `frontend/src/test/mocks/handlers.ts`, follow its existing `http.post('*/auth/forgot-password', ...)` style; add:
  - `http.get('*/auth/account/me')` → 200 AccountResponse fixture
  - `http.patch('*/auth/account/me')` → 200 `{account, tokens: null}` (and a mismatch variant is unnecessary — validation is client-side)
  - `http.post('*/auth/account/change-password')` → 200 AuthResponse fixture
  - `http.delete('*/auth/account/me')` → 204

- [ ] **Step 5: Verify**

Run: `cd frontend && npx tsc -b --noEmit && npm run lint`
Expected: clean.

---

### Task 8: frontend — AccountSettingsPage + routes + nav entries

**Files:**
- Create: `frontend/src/pages/customer/AccountSettingsPage.tsx`
- Modify: `frontend/src/utils/constants.ts` (ROUTES + QUERY_KEYS entries)
- Modify: `frontend/src/routes/index.tsx` (3 routes)
- Modify: nav components — read `WORKING_STATE_frontend.md` rules FIRST; then: the customer account nav (find where `/account/profile` is linked — Grep `ROUTES.PROFILE` in `frontend/src` — and add a Settings link beside it), `SELLER_NAV` in `frontend/src/routes/layouts/SellerLayout.tsx` (one "Account" item → `/seller/account`), `ADMIN_NAV` in `frontend/src/routes/layouts/AdminLayout.tsx` (one "Account" item → `/admin/account`). Do NOT remove or reorder the required items (Seller: Dashboard, Products, Orders, Inventory, Business profile; Admin: Dashboard, Tenants, Users, Payments, Analytics).

**Interfaces:**
- Consumes: Task 7 API + store; MUI v9 (`slotProps`), RHF + Zod, `useUIStore.addToast`, `QUERY_KEYS`.
- Produces: routes `/account/settings`, `/seller/account`, `/admin/account` all rendering `AccountSettingsPage`; `ROUTES.ACCOUNT_SETTINGS`, `ROUTES.SELLER_ACCOUNT`, `ROUTES.ADMIN_ACCOUNT`; `QUERY_KEYS.ACCOUNT = 'account'`.

- [ ] **Step 1: constants** — add to `ROUTES`: `ACCOUNT_SETTINGS: '/account/settings'`, `SELLER_ACCOUNT: '/seller/account'`, `ADMIN_ACCOUNT: '/admin/account'`; add to `QUERY_KEYS`: `ACCOUNT: 'account'`.

- [ ] **Step 2: the page** — `AccountSettingsPage.tsx`. Structure (single component, three cards):

```tsx
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box, Button, CircularProgress, Container, Dialog, DialogActions, DialogContent,
  DialogContentText, DialogTitle, Divider, IconButton, InputAdornment, Paper,
  TextField, Typography,
} from '@mui/material';
import Visibility from '@mui/icons-material/Visibility';
import VisibilityOff from '@mui/icons-material/VisibilityOff';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { authApi } from '@api/auth.api';
import { QUERY_KEYS, ROUTES } from '@utils/constants';
import { useAuthStore } from '@stores/auth.store';
import { useUIStore } from '@stores/ui.store';

const identitySchema = z
  .object({
    firstname: z.string().min(1, 'Required'),
    lastname: z.string().min(1, 'Required'),
    email: z.string().email('Invalid email'),
    currentPassword: z.string().optional(),
  });
type IdentityValues = z.infer<typeof identitySchema>;

const passwordSchema = z
  .object({
    currentPassword: z.string().min(1, 'Required'),
    newPassword: z.string().min(8, 'At least 8 characters'),
    confirmPassword: z.string().min(1, 'Required'),
  })
  .refine((v) => v.newPassword === v.confirmPassword, {
    message: 'Passwords do not match',
    path: ['confirmPassword'],
  });
type PasswordValues = z.infer<typeof passwordSchema>;

export default function AccountSettingsPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const addToast = useUIStore((s) => s.addToast);
  const { role, setTokens, setEmail, clearAuth } = useAuthStore();

  const { data: account, isLoading } = useQuery({
    queryKey: [QUERY_KEYS.ACCOUNT],
    queryFn: () => authApi.getAccount(),
  });

  // ── Identity form ──
  const identityForm = useForm<IdentityValues>({ resolver: zodResolver(identitySchema) });
  useEffect(() => {
    if (account) {
      identityForm.reset({
        firstname: account.firstname,
        lastname: account.lastname,
        email: account.email,
        currentPassword: '',
      });
    }
  }, [account]); // eslint-disable-line react-hooks/exhaustive-deps

  const watchedEmail = identityForm.watch('email');
  const emailChanged = !!account && !!watchedEmail
    && watchedEmail.toLowerCase() !== account.email.toLowerCase();

  const updateIdentity = useMutation({
    mutationFn: (v: IdentityValues) =>
      authApi.updateAccount({
        firstname: v.firstname,
        lastname: v.lastname,
        email: v.email,
        currentPassword: emailChanged ? v.currentPassword : undefined,
      }),
    onSuccess: (res) => {
      if (res.tokens) {
        // Email changed: the old JWT subject is dead — adopt the fresh pair immediately.
        setTokens(res.tokens.accessToken, res.tokens.refreshToken);
        setEmail(res.tokens.email);
      }
      queryClient.invalidateQueries({ queryKey: [QUERY_KEYS.ACCOUNT] });
      addToast({ message: 'Account updated', variant: 'success' });
    },
    onError: (err: { response?: { data?: { message?: string } } }) => {
      addToast({
        message: err.response?.data?.message ?? 'Failed to update account',
        variant: 'error',
      });
    },
  });

  // ── Password form ──
  const [showPw, setShowPw] = useState(false);
  const passwordForm = useForm<PasswordValues>({ resolver: zodResolver(passwordSchema) });
  const changePassword = useMutation({
    mutationFn: (v: PasswordValues) => authApi.changePassword(v),
    onSuccess: (tokens) => {
      setTokens(tokens.accessToken, tokens.refreshToken);
      passwordForm.reset({ currentPassword: '', newPassword: '', confirmPassword: '' });
      addToast({ message: 'Password changed', variant: 'success' });
    },
    onError: (err: { response?: { data?: { message?: string } } }) => {
      addToast({
        message: err.response?.data?.message ?? 'Failed to change password',
        variant: 'error',
      });
    },
  });

  // ── Danger zone (USER only) ──
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [deletePassword, setDeletePassword] = useState('');
  const deleteAccount = useMutation({
    mutationFn: () => authApi.deleteAccount(deletePassword),
    onSuccess: () => {
      clearAuth();
      addToast({ message: 'Your account has been deleted', variant: 'success' });
      navigate(ROUTES.HOME);
    },
    onError: (err: { response?: { data?: { message?: string } } }) => {
      addToast({
        message: err.response?.data?.message ?? 'Failed to delete account',
        variant: 'error',
      });
    },
  });

  if (isLoading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', py: 12 }}>
        <CircularProgress />
      </Box>
    );
  }

  return (
    <Container maxWidth="sm" sx={{ py: 6 }}>
      <Typography variant="h3" sx={{ fontFamily: 'var(--font-serif)', mb: 5 }}>
        Account settings
      </Typography>

      {/* ── Identity ── */}
      <Paper sx={{ p: 3, mb: 4 }}>
        <Typography variant="h6" sx={{ mb: 2 }}>Identity</Typography>
        <Box
          component="form"
          onSubmit={identityForm.handleSubmit((v) => updateIdentity.mutateAsync(v))}
          sx={{ display: 'flex', flexDirection: 'column', gap: 2.5 }}
        >
          <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2 }}>
            <TextField
              {...identityForm.register('firstname')}
              label="First name"
              error={!!identityForm.formState.errors.firstname}
              helperText={identityForm.formState.errors.firstname?.message}
              fullWidth
            />
            <TextField
              {...identityForm.register('lastname')}
              label="Last name"
              error={!!identityForm.formState.errors.lastname}
              helperText={identityForm.formState.errors.lastname?.message}
              fullWidth
            />
          </Box>
          <TextField
            {...identityForm.register('email')}
            label="Email (used to sign in)"
            type="email"
            error={!!identityForm.formState.errors.email}
            helperText={identityForm.formState.errors.email?.message}
            fullWidth
          />
          {emailChanged && (
            <TextField
              {...identityForm.register('currentPassword')}
              label="Current password (required to change email)"
              type="password"
              helperText="Changing your sign-in email requires your current password."
              fullWidth
            />
          )}
          <Button
            type="submit"
            variant="contained"
            disabled={identityForm.formState.isSubmitting}
          >
            {identityForm.formState.isSubmitting
              ? <CircularProgress size={22} color="inherit" />
              : 'Save identity'}
          </Button>
        </Box>
      </Paper>

      {/* ── Change password ── */}
      <Paper sx={{ p: 3, mb: 4 }}>
        <Typography variant="h6" sx={{ mb: 2 }}>Change password</Typography>
        <Box
          component="form"
          onSubmit={passwordForm.handleSubmit((v) => changePassword.mutateAsync(v))}
          sx={{ display: 'flex', flexDirection: 'column', gap: 2.5 }}
        >
          <TextField
            {...passwordForm.register('currentPassword')}
            label="Current password"
            type={showPw ? 'text' : 'password'}
            error={!!passwordForm.formState.errors.currentPassword}
            helperText={passwordForm.formState.errors.currentPassword?.message}
            fullWidth
            slotProps={{
              input: {
                endAdornment: (
                  <InputAdornment position="end">
                    <IconButton onClick={() => setShowPw((s) => !s)} edge="end" size="small">
                      {showPw ? <VisibilityOff /> : <Visibility />}
                    </IconButton>
                  </InputAdornment>
                ),
              },
            }}
          />
          <TextField
            {...passwordForm.register('newPassword')}
            label="New password"
            type={showPw ? 'text' : 'password'}
            error={!!passwordForm.formState.errors.newPassword}
            helperText={passwordForm.formState.errors.newPassword?.message}
            fullWidth
          />
          <TextField
            {...passwordForm.register('confirmPassword')}
            label="Confirm new password"
            type={showPw ? 'text' : 'password'}
            error={!!passwordForm.formState.errors.confirmPassword}
            helperText={passwordForm.formState.errors.confirmPassword?.message}
            fullWidth
          />
          <Button
            type="submit"
            variant="contained"
            disabled={passwordForm.formState.isSubmitting}
          >
            {passwordForm.formState.isSubmitting
              ? <CircularProgress size={22} color="inherit" />
              : 'Change password'}
          </Button>
        </Box>
      </Paper>

      {/* ── Danger zone — customers only ── */}
      {role === 'USER' && (
        <Paper sx={{ p: 3, borderColor: 'error.main', borderWidth: 1, borderStyle: 'solid' }}>
          <Typography variant="h6" color="error" sx={{ mb: 1 }}>Danger zone</Typography>
          <Typography variant="body2" sx={{ color: 'text.secondary', mb: 2 }}>
            Deleting your account signs you out everywhere and removes your profile.
            Your past orders remain on record. This cannot be undone.
          </Typography>
          <Button variant="outlined" color="error" onClick={() => setDeleteOpen(true)}>
            Delete my account
          </Button>
          <Dialog open={deleteOpen} onClose={() => setDeleteOpen(false)}>
            <DialogTitle>Delete your account?</DialogTitle>
            <DialogContent>
              <DialogContentText sx={{ mb: 2 }}>
                Enter your password to confirm. This action is permanent.
              </DialogContentText>
              <TextField
                autoFocus
                label="Password"
                type="password"
                value={deletePassword}
                onChange={(e) => setDeletePassword(e.target.value)}
                fullWidth
              />
            </DialogContent>
            <DialogActions>
              <Button onClick={() => setDeleteOpen(false)}>Cancel</Button>
              <Button
                color="error"
                variant="contained"
                disabled={!deletePassword || deleteAccount.isPending}
                onClick={() => deleteAccount.mutate()}
              >
                {deleteAccount.isPending
                  ? <CircularProgress size={20} color="inherit" />
                  : 'Delete account'}
              </Button>
            </DialogActions>
          </Dialog>
        </Paper>
      )}
      <Divider sx={{ mt: 4, opacity: 0 }} />
    </Container>
  );
}
```

(If `@mui/icons-material` Visibility icons aren't already used elsewhere, check how LoginPage does its show/hide toggle and reuse THAT exact mechanism instead.)

- [ ] **Step 3: routes** — in `frontend/src/routes/index.tsx`:
  - Add lazy import: `const AccountSettingsPage = () => lazy_page(() => import('@pages/customer/AccountSettingsPage'));`
  - Customer children: `{ path: '/account/settings', element: <AccountSettingsPage /> },`
  - Seller children: `{ path: '/seller/account', element: <AccountSettingsPage /> },`
  - Admin children: `{ path: '/admin/account', element: <AccountSettingsPage /> },`

- [ ] **Step 4: nav entries** — Grep `ROUTES.PROFILE` to find the customer account menu; add a "Settings" item pointing at `ROUTES.ACCOUNT_SETTINGS` next to it. Add one `{ label: 'Account', path: '/seller/account' }`-shaped item to `SELLER_NAV` and `ADMIN_NAV` (match each array's exact item shape/icon pattern; append at the END so required items keep their order).

- [ ] **Step 5: Verify**

Run: `cd frontend && npm run build && npm run lint`
Expected: both green.

---

### Task 9: frontend — trim ProfilePage to address-only

**Files:**
- Modify: `frontend/src/pages/customer/ProfilePage.tsx`

**Interfaces:**
- Consumes: `customersApi.getById/update` (unchanged — `CustomerRequest` still requires firstname/lastname/email, so the update sends the FETCHED values untouched).
- Produces: `/account/profile` = delivery address editor only; name/email shown read-only with a link to `/account/settings`.

- [ ] **Step 1: Rewrite the form section**

- Zod schema shrinks to the 5 address fields only (`street, houseNumber, city, zipCode, country`).
- `reset()` populates only those fields.
- The mutation sends `firstname: customer!.firstname, lastname: customer!.lastname, email: customer!.email` plus the form's address values, so nothing else changes server-side.
- Replace the name/email `TextField`s with a read-only block:

```tsx
      <Paper variant="outlined" sx={{ p: 2, mb: 3, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <Box>
          <Typography variant="body1">{customer?.firstname} {customer?.lastname}</Typography>
          <Typography variant="body2" sx={{ color: 'text.secondary' }}>{customer?.email}</Typography>
        </Box>
        <Button component={RouterLink} to={ROUTES.ACCOUNT_SETTINGS} size="small">
          Edit in account settings
        </Button>
      </Paper>
```
(new imports: `Paper` from `@mui/material`, `Link as RouterLink` from `react-router-dom`, `ROUTES` from `@utils/constants`.)

- [ ] **Step 2: Verify**

Run: `cd frontend && npm run build && npm run lint`
Expected: green. Also run: `npx tsc -b --noEmit` if build output is ambiguous.

---

### Task 10: frontend — Playwright e2e spec + full frontend verification

**Files:**
- Create: `frontend/e2e/account-settings.spec.ts`
- Reference: `frontend/e2e/auth.spec.ts` for the auth-seeding helper (MUST seed via **sessionStorage**, key `obsidian-auth` — the localStorage variant regressed all E2E once; memory `project_e2e_auth_sessionstorage_regression`).

**Interfaces:**
- Consumes: routes + page from Task 8. E2E runs against the mocked/dev server per the existing playwright config — follow whatever `auth.spec.ts` does for API mocking (`page.route` interceptions or MSW).

- [ ] **Step 1: Write the spec** (follow `auth.spec.ts` structure exactly for seeding + route mocking; cover):
  1. USER visits `/account/settings` → all three sections render (Identity, Change password, Danger zone).
  2. SELLER visits `/seller/account` → Identity + Change password render, Danger zone absent.
  3. Password mismatch in Change-password form → inline "Passwords do not match", no request fired.
  4. Editing the email field reveals the current-password field.
  5. Delete dialog: button disabled until password typed.

- [ ] **Step 2: Run the whole e2e suite**

Run: `cd frontend && npx playwright test`
Expected: previous 75 + new tests ALL green.

- [ ] **Step 3: Final frontend gate**

Run: `cd frontend && npm run build && npm run lint`
Expected: green.

---

### Task 11: Full verification + memory + handoff

- [ ] **Step 1: Full single-thread module suites** (never `-T` parallel — RAM crash risk per memory):

```
mvn test -pl authentication-service
mvn test -pl customer-service
```
Expected: both BUILD SUCCESS, zero failures/errors.

- [ ] **Step 2: Update memory** — `project_account_mgmt_feature_series.md`: mark Task 3 implemented (tests green, awaiting container rebuild + live proof), summarizing endpoints, soft-delete semantics, internal sync, FE pages.

- [ ] **Step 3: Notify the user** — per the no-Docker rule, report: which containers need rebuild (`authentication-service`, `customer-service`, frontend), that NO DB migration is needed, and that live verification (register throwaway → edit name/email/password → delete → re-register) is the remaining proof step. Do NOT claim "done" — tests green ≠ production proof (memory `feedback_critical_failures`).

---

## Self-Review Notes

- Spec coverage: §1 endpoints → Tasks 1–4; §2 propagation → Tasks 3+6; §3 frontend → Tasks 7–9; §4 error table → Task 4 slice tests + Task 5 BDD; §5 test layers → Tasks 1–6 (backend ×3 layers), 7–10 (frontend); out-of-scope respected (no seller/admin delete, no email-verification mail, no Kafka).
- Type consistency: `AccountUpdateResponse(account, tokens)` used identically in service (Task 1), controller test JSON paths (Task 4), BDD jsonPath `tokens.accessToken` (Task 5), and TS `AccountUpdateResponse` (Task 7). Tombstone `deleted-<id>@removed.local` consistent between Task 2 code and test.
- Known flexibility points (explicitly instructed, not placeholders): customer-service test scaffolding follows that module's existing test classes; MSW handler file style; nav item shape; LoginPage show/hide mechanism.
