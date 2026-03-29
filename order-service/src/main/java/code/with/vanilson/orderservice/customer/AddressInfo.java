package code.with.vanilson.orderservice.customer;

/**
 * AddressInfo
 * <p>
 * Local DTO owned exclusively by order-service.
 * Represents address data as received from customer-service via HTTP.
 * No dependency on customer-service JAR.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
public record AddressInfo(
        String street,
        String houseNumber,
        String zipCode
) {
}
