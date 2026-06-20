package code.with.vanilson.orderservice.product;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * ProductOwnerResponse
 * <p>
 * Minimal local DTO owned by order-service. Captures only the fields of
 * product-service's {@code ProductResponse} that order-service needs to stamp
 * ownership on an order line: the product id and its {@code createdBy} (the
 * owning seller's userId). Unknown JSON properties are ignored so the full
 * product payload deserialises without coupling to product-service's DTO.
 *
 * @author vamuhong
 * @version 1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductOwnerResponse(
        Integer id,
        String createdBy
) {
}
