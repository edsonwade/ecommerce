package code.with.vanilson.authentication.presentation;

import code.with.vanilson.authentication.application.SellerProfileRequest;
import code.with.vanilson.authentication.application.SellerProfileResponse;
import code.with.vanilson.authentication.application.SellerProfileService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SellerProfileController — Presentation Layer
 * <p>
 * Exposes the seller business profile (the "sold by" identity on an invoice):
 * <ul>
 *   <li>{@code GET /api/v1/auth/sellers/{id}} — any authenticated user reads a seller's public
 *       business identity (a buyer must see who they bought from on their order detail).</li>
 *   <li>{@code PUT /api/v1/auth/sellers/me} — the authenticated user updates their own profile.</li>
 * </ul>
 *
 * @author vamuhong
 */
@RestController
@RequestMapping("/api/v1/auth/sellers")
@RequiredArgsConstructor
@Tag(name = "Seller Profile", description = "Seller business/invoice identity")
public class SellerProfileController {

    private final SellerProfileService service;

    @Operation(summary = "Get a seller's public business profile (invoice 'sold by' identity)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Seller profile returned"),
            @ApiResponse(responseCode = "404", description = "Seller not found")
    })
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}")
    public ResponseEntity<SellerProfileResponse> getSeller(@PathVariable Long id) {
        return ResponseEntity.ok(service.getSellerProfile(id));
    }

    @Operation(summary = "Update my own seller business profile")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile updated"),
            @ApiResponse(responseCode = "400", description = "Invalid payload")
    })
    @PreAuthorize("isAuthenticated()")
    @PutMapping("/me")
    public ResponseEntity<SellerProfileResponse> updateMyProfile(
            @AuthenticationPrincipal UserDetailsAdapter actor,
            @RequestBody @Valid SellerProfileRequest request) {
        return ResponseEntity.ok(service.updateMyProfile(actor.getUser().getId(), request));
    }
}
