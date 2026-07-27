package code.with.vanilson.tenantcontext.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * InternalTokenAutoConfiguration — makes the S2S building blocks available to any service that
 * embeds {@code tenant-context}, without any of them wiring the security logic by hand.
 * <p>
 * Only the {@link InternalTokenAuthenticator} is contributed automatically, because it is inert on
 * its own: it holds configuration and answers questions, and with nothing configured it answers "no"
 * to everything. The {@link InternalTokenAuthenticationFilter} is deliberately <strong>not</strong>
 * auto-registered — a servlet filter bean is picked up by the container for every request, so
 * publishing one from a shared library would silently change the filter chain of every service that
 * upgrades. Services that expose {@code /internal} declare it themselves and control its position in
 * the security chain (order-service registers it before the JWT filter).
 * <p>
 * Likewise {@link InternalTokenRequestInterceptor} is not a bean here: a global Feign interceptor
 * would attach the internal secret to <em>every</em> outbound client. Callers build it per Feign
 * client instead.
 *
 * @author vamuhong
 * @version 1.0
 */
@Configuration
@ConditionalOnClass(ObjectMapper.class)
@EnableConfigurationProperties(InternalTokenProperties.class)
public class InternalTokenAutoConfiguration {

    /**
     * Builds the authenticator, with one deliberate rule about the legacy secret.
     * <p>
     * <strong>The legacy single secret is honoured ONLY while no per-caller secret is configured.</strong>
     * It exists so a service that has not migrated yet keeps working, not as a permanent parallel way in.
     * The moment {@code application.security.internal.accepted} names even one caller, the legacy value is
     * ignored — otherwise a platform-wide shared string, which by construction cannot prove who holds it,
     * would silently bypass the per-caller identity the map was configured to enforce. Migration is
     * therefore a one-way door: add the {@code accepted} entry and the weaker credential stops working in
     * the same deployment, with no separate cleanup step to forget.
     *
     * @param legacyToken the pre-existing {@code application.security.internal-token}
     */
    @Bean
    @ConditionalOnMissingBean
    public InternalTokenAuthenticator internalTokenAuthenticator(
            InternalTokenProperties properties,
            @Value("${application.security.internal-token:}") String legacyToken) {
        boolean perCallerConfigured = properties.getAccepted() != null
                && properties.getAccepted().values().stream()
                .filter(java.util.Objects::nonNull)
                .flatMap(java.util.List::stream)
                .anyMatch(token -> token != null && !token.isBlank());

        return new InternalTokenAuthenticator(
                properties.getAccepted(),
                perCallerConfigured ? "" : legacyToken,
                properties.isRequireCallerHeader());
    }
}
