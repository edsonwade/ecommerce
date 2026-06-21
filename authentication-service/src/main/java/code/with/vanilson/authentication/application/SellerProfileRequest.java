package code.with.vanilson.authentication.application;

import jakarta.validation.constraints.Size;

/**
 * SellerProfileRequest — payload for a seller editing their own business profile via
 * {@code PUT /api/v1/auth/sellers/me}. All fields optional; only non-blank values are
 * merged, so a partial update never wipes existing data.
 *
 * @author vamuhong
 */
public record SellerProfileRequest(
        @Size(max = 255, message = "Company name must be at most 255 characters")
        String companyName,

        @Size(max = 64, message = "VAT number must be at most 64 characters")
        String vatNumber,

        @Size(max = 256, message = "Street must be at most 256 characters")
        String street,

        @Size(max = 128, message = "City must be at most 128 characters")
        String city,

        @Size(max = 128, message = "Country must be at most 128 characters")
        String country,

        @Size(max = 64, message = "Postal code must be at most 64 characters")
        String postalCode
) {
}
