package code.with.vanilson.paymentservice.domain;

/**
 * CustomerData — Domain DTO (local to payment-service)
 * <p>
 * Represents the customer information included in a payment request.
 * Owned exclusively by payment-service — replaces the import of
 * code.with.vanilson.customerservice.Customer from the original code.
 * <p>
 * No cross-service JAR dependency — data arrives via HTTP JSON payload.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
public record CustomerData(
        String customerId,
        String firstname,
        String lastname,
        String email
) {
}
