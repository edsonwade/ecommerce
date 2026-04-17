package code.with.vanilson.authentication.presentation;

import code.with.vanilson.authentication.application.UpdateRoleRequest;
import code.with.vanilson.authentication.application.UserManagementService;
import code.with.vanilson.authentication.application.UserSummaryResponse;
import code.with.vanilson.authentication.domain.UserDetailsAdapter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/users")
@RequiredArgsConstructor
@Tag(name = "User Management", description = "ADMIN-only user listing and role management")
public class UserManagementController {

    private final UserManagementService service;

    @Operation(summary = "List all users (paginated) — ADMIN only")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<UserSummaryResponse>> listUsers(
            @PageableDefault(size = 25) Pageable pageable) {
        return ResponseEntity.ok(service.listUsers(pageable));
    }

    @Operation(summary = "Promote or demote a user's role — ADMIN only")
    @PatchMapping("/{userId}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserSummaryResponse> updateRole(
            @AuthenticationPrincipal UserDetailsAdapter actor,
            @PathVariable Long userId,
            @RequestBody @Valid UpdateRoleRequest request) {
        return ResponseEntity.ok(service.updateRole(
                actor.getUsername(),
                actor.getUser().getId(),
                userId,
                request.role()));
    }
}
