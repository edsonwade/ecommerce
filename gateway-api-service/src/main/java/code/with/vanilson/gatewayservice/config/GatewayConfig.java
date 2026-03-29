package code.with.vanilson.gatewayservice.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.web.reactive.config.EnableWebFlux;

import java.nio.charset.StandardCharsets;

/**
 * GatewayConfig
 * <p>
 * Central configuration for:
 * - MessageSource: loads messages.properties for all error/log messages
 * - RedisRateLimiter: token-bucket rate limiting backed by Redis
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
@Configuration
@EnableWebFlux
public class GatewayConfig {

    /**
     * MessageSource backed by messages.properties.
     * All exception messages and log messages must use this bean.
     * Never hardcode user-facing strings in Java classes.
     */
    @Bean
    public MessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasenames("messages");
        source.setDefaultEncoding(StandardCharsets.UTF_8.name());
        source.setUseCodeAsDefaultMessage(false);
        return source;
    }

    /**
     * Redis-backed token bucket rate limiter.
     * replenishRate  = tokens added per second (sustained throughput)
     * burstCapacity  = max tokens in bucket (allows short bursts)
     * requestedTokens = tokens consumed per request (default 1)
     *
     * Example: replenishRate=100, burstCapacity=200
     *   → Normal: 100 req/s sustained
     *   → Burst: up to 200 req/s for a short window
     *   → After burst: drains to 100 req/s rate
     *
     * Per-tenant limits are enforced via TenantKeyResolver bucket keys in Redis.
     * The actual per-tenant tier limits are configured in gateway-service.yml.
     */
    @Bean
    public RedisRateLimiter redisRateLimiter() {
        return new RedisRateLimiter(
                100,   // replenishRate: 100 tokens/sec (base tier)
                200,   // burstCapacity: burst up to 200
                1      // requestedTokens: 1 token per request
        );
    }
}
