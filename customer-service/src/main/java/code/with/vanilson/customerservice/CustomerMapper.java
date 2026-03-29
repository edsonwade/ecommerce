package code.with.vanilson.customerservice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * CustomerMapper — Application Layer
 * <p>
 * Pure mapping between Customer entity and request/response DTOs.
 * Single Responsibility (SOLID-S): only maps — never throws business exceptions.
 * <p>
 * BUG FIXED: original mapper threw CustomerBadRequestException with hardcoded strings.
 * Mapper should never throw business exceptions — that is the service's responsibility.
 * Null input returns null; callers must guard before using the result.
 * </p>
 *
 * @author vamuhong
 * @version 3.0
 */
@Component
@Slf4j
public class CustomerMapper {

    /**
     * Maps a CustomerRequest DTO to a Customer entity for persistence.
     *
     * @param request the validated customer request
     * @return Customer entity, or null if request is null
     */
    public Customer toEntity(CustomerRequest request) {
        if (request == null) {
            log.warn("[CustomerMapper] toEntity called with null request");
            return null;
        }
        return Customer.builder()
                .customerId(request.customerId())
                .firstname(request.firstname())
                .lastname(request.lastname())
                .email(request.email())
                .address(request.address())
                .build();
    }

    /**
     * Maps a Customer entity to a CustomerResponse DTO.
     *
     * @param customer the persisted Customer entity
     * @return CustomerResponse DTO, or null if customer is null
     */
    public CustomerResponse toResponse(Customer customer) {
        if (customer == null) {
            log.warn("[CustomerMapper] toResponse called with null customer");
            return null;
        }
        return new CustomerResponse(
                customer.getCustomerId(),
                customer.getFirstname(),
                customer.getLastname(),
                customer.getEmail(),
                customer.getAddress()
        );
    }

    /**
     * Maps a Customer entity to a CustomerRequest DTO.
     * Used when the request representation is needed (e.g. Feign client contracts).
     *
     * @param customer the Customer entity
     * @return CustomerRequest DTO, or null if customer is null
     */
    public CustomerRequest toRequest(Customer customer) {
        if (customer == null) {
            log.warn("[CustomerMapper] toRequest called with null customer");
            return null;
        }
        return new CustomerRequest(
                customer.getCustomerId(),
                customer.getFirstname(),
                customer.getLastname(),
                customer.getEmail(),
                customer.getAddress()
        );
    }
}
