package code.with.vanilson.orderservice.customer;

/**
 * CustomerInfo
 * <p>
 * Local DTO owned exclusively by order-service.
 * Represents the subset of customer data needed for order processing.
 * <p>
 * This record is the payload returned by the CustomerClient Feign call.
 * order-service MUST NOT import any class from customer-service JAR.
 * Contract is API-based (HTTP JSON) only.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
public record CustomerInfo(
        String customerId,
        String firstname,
        String lastname,
        String email,
        AddressInfo address
) {
}
