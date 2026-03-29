package code.with.vanilson.orderservice.payment;

import code.with.vanilson.orderservice.exception.PaymentServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * PaymentClient
 * <p>
 * Feign client owned by order-service for communicating with payment-service.
 * <p>
 * BUG FIXED: Original had name="product-service" — now correctly "payment-service".
 * Circuit breaker fallback: when payment-service is down, order is saved in PENDING
 * state and PaymentServiceUnavailableException is thrown — order is NOT lost.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
@FeignClient(
        name = "payment-service",
        url = "${application.config.payment-url}",
        fallbackFactory = PaymentClient.PaymentClientFallbackFactory.class
)
public interface PaymentClient {

    @PostMapping
    Integer requestOrderPayment(@RequestBody PaymentRequest request);

    // -------------------------------------------------------
    // Fallback factory — inner class for cohesion
    // -------------------------------------------------------

    @Slf4j
    @Component
    class PaymentClientFallbackFactory implements FallbackFactory<PaymentClient> {

        private final MessageSource messageSource;

        PaymentClientFallbackFactory(MessageSource messageSource) {
            this.messageSource = messageSource;
        }

        @Override
        public PaymentClient create(Throwable cause) {
            return request -> {
                String message = messageSource.getMessage(
                        "order.payment.service.unavailable",
                        null,
                        LocaleContextHolder.getLocale());
                log.error("[PaymentClientFallback] payment-service unavailable for orderId=[{}] orderRef=[{}]. Cause: {}",
                        request.orderId(), request.orderReference(), cause.getMessage());
                throw new PaymentServiceUnavailableException(message, "order.payment.service.unavailable", cause);
            };
        }
    }
}
