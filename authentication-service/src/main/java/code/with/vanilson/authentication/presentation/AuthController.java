package code.with.vanilson.authentication.presentation;

import code.with.vanilson.authentication.application.AuthResponse;
import code.with.vanilson.authentication.application.AuthService;
import code.with.vanilson.authentication.application.LoginRequest;
import code.with.vanilson.authentication.application.RegisterRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AuthController — Presentation Layer
 * <p>
 * REST endpoints for authentication lifecycle.
 * Single Responsibility (SOLID-S): HTTP concerns only — delegates all logic to AuthService.
 * <p>
 * Endpoints:
 * POST /api/v1/auth/register  → register new user (public)
 * POST /api/v1/auth/login     → authenticate + receive JWT pair (public)
 * POST /api/v1/auth/refresh   → refresh access token using refresh token (requires Bearer)
 * POST /api/v1/auth/logout    → revoke current token (requires Bearer)
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication API", description = "JWT-based authentication and authorisation")
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Register a new user",
               description = "Creates a new user account and returns a JWT access + refresh token pair.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "User registered successfully"),
        @ApiResponse(responseCode = "409", description = "Email already in use"),
        @ApiResponse(responseCode = "400", description = "Invalid request payload")
    })
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody @Valid RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @Operation(summary = "Login",
               description = "Authenticates credentials and returns a JWT access + refresh token pair.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Login successful"),
        @ApiResponse(responseCode = "401", description = "Invalid credentials")
    })
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody @Valid LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @Operation(summary = "Refresh access token",
               description = "Exchanges a valid refresh token for a new access + refresh token pair.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Tokens refreshed"),
        @ApiResponse(responseCode = "401", description = "Invalid or expired refresh token")
    })
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest request) {
        return ResponseEntity.ok(authService.refreshToken(request));
    }

    @Operation(summary = "Logout",
               description = "Revokes the current access token. Subsequent requests with this token are rejected.")
    @ApiResponse(responseCode = "204", description = "Logged out successfully")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        authService.logout(request);
        return ResponseEntity.noContent().build();
    }
}
