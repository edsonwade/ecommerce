package code.with.vanilson.orderservice.product;

import code.with.vanilson.orderservice.exception.ProductServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import java.util.List;

/**
 * ProductClient — Infrastructure Layer (HTTP)
 * <p>
 * Phase 2 change: RestTemplate → WebClient (non-blocking).
 * <p>
 * Key improvements:
 * 1. NON-BLOCKING: WebClient does not hold a thread while waiting for the response.
 *    Under 10k concurrent requests, RestTemplate would exhaust the thread pool.
 *    WebClient uses Netty's event loop — orders of magnitude more scalable.
 * 2. TYPED error handling: onStatus() maps HTTP error codes to specific exceptions.
 * 3. TIMEOUT: inherited from WebClient.Builder (3s connect, 5s read).
 * 4. SYNCHRONOUS call pattern (.block()) is kept intentionally for Phase 2 —
 *    the OrderService flow is still synchronous at the service layer.
 *    Phase 3 will remove .block() entirely and use Kafka for stock reservation.
 * <p>
 * Trade-off of .block(): we lose the non-blocking benefit at the service layer
 * but gain it at the HTTP transport layer (connection pooling, socket reuse).
 * Full reactive pipeline requires OrderService to return Mono<Integer> — Phase 3.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
@Slf4j
@Service
public class ProductClient {

    @Value("${application.config.product-url}")
    private String productUrl;

    private final WebClient    webClient;
    private final MessageSource messageSource;

    public ProductClient(WebClient webClient, MessageSource messageSource) {
        this.webClient     = webClient;
        this.messageSource = messageSource;
    }

    /**
     * Calls product-service to reserve stock for a list of products.
     * Uses WebClient (non-blocking transport) with synchronous .block() at the boundary.
     *
     * @param requestBody list of products and quantities to reserve
     * @return list of purchased product details from product-service
     * @throws ProductServiceUnavailableException if the service is down or returns an error
     */
    public List<ProductPurchaseResponse> purchaseProducts(List<ProductPurchaseRequest> requestBody) {
        log.info("[ProductClient] Reserving stock for {} products via WebClient", requestBody.size());

        try {
            List<ProductPurchaseResponse> result = webClient
                    .post()
                    .uri(productUrl + "/purchase")
                    .bodyValue(requestBody)
                    .retrieve()
                    // 4xx client errors → mapped to ProductServiceUnavailableException
                    .onStatus(HttpStatusCode::is4xxClientError, response ->
                            response.bodyToMono(String.class)
                                    .map(body -> new ProductServiceUnavailableException(
                                            resolve("order.product.purchase.failed"),
                                            "order.product.purchase.failed")))
                    // 5xx server errors → mapped to ProductServiceUnavailableException
                    .onStatus(HttpStatusCode::is5xxServerError, response ->
                            response.bodyToMono(String.class)
                                    .map(body -> new ProductServiceUnavailableException(
                                            resolve("order.product.service.unavailable"),
                                            "order.product.service.unavailable")))
                    .bodyToMono(new ParameterizedTypeReference<List<ProductPurchaseResponse>>() {})
                    // synchronous boundary — Phase 3 will remove this
                    .block();

            if (result == null || result.isEmpty()) {
                throw new ProductServiceUnavailableException(
                        resolve("order.product.purchase.failed"), "order.product.purchase.failed");
            }

            log.info("[ProductClient] Stock reserved successfully for {} products", result.size());
            return result;

        } catch (ProductServiceUnavailableException ex) {
            throw ex;
        } catch (WebClientRequestException ex) {
            // Connection refused, timeout, etc.
            log.error("[ProductClient] product-service connection failed: {}", ex.getMessage());
            throw new ProductServiceUnavailableException(
                    resolve("order.product.service.unavailable"),
                    "order.product.service.unavailable", ex);
        } catch (Exception ex) {
            log.error("[ProductClient] Unexpected error calling product-service: {}", ex.getMessage(), ex);
            throw new ProductServiceUnavailableException(
                    resolve("order.product.service.unavailable"),
                    "order.product.service.unavailable", ex);
        }
    }

    private String resolve(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }
}
