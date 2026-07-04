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
