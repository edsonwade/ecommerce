package code.with.vanilson.authentication.application;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * SellerProfileResponse — the public business identity of a seller, used as the "sold by"
 * block on an order invoice. Returned by {@code GET /api/v1/auth/sellers/{id}}.
 * <p>
 * Contains no credentials or security state — only the legal/contact identity a buyer is
 * entitled to see on their invoice. Null business fields are omitted from JSON ({@code NON_EMPTY})
 * so a seller who hasn't completed their profile simply surfaces name + email.
 * </p>
 *
 * @author vamuhong
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SellerProfileResponse(
        Long id,
        String fullName,
        String firstname,
        String lastname,
        String email,
        String companyName,
        String vatNumber,
        String street,
        String city,
        String country,
        String postalCode
) {
}
