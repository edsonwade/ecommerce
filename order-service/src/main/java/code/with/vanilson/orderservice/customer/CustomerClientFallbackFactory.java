package code.with.vanilson.orderservice.customer;

import code.with.vanilson.orderservice.exception.CustomerServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * CustomerClientFallbackFactory
 * <p>
 * Feign fallback factory for CustomerClient.
 * When the customer-service is unreachable or times out, this fallback is invoked.
 * Throws CustomerServiceUnavailableException with message from messages.properties.
 * The cause (original Feign exception) is preserved for logging purposes.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
@Slf4j
@Component
public class CustomerClientFallbackFactory implements FallbackFactory<CustomerClient> {

    private final MessageSource messageSource;

    public CustomerClientFallbackFactory(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @Override
    public CustomerClient create(Throwable cause) {
        return customerId -> {
            String message = messageSource.getMessage(
                    "order.customer.service.unavailable",
                    null,
                    LocaleContextHolder.getLocale());
            log.error("[CustomerClientFallback] customer-service unavailable for customerId=[{}]. Cause: {}",
                    customerId, cause.getMessage());
            throw new CustomerServiceUnavailableException(message, "order.customer.service.unavailable", cause);
        };
    }
}
