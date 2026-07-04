package code.with.vanilson.authentication.application;

import code.with.vanilson.authentication.config.AsyncConfig;
import code.with.vanilson.authentication.domain.User;
import code.with.vanilson.authentication.infrastructure.CustomerRegistrationClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * CustomerProvisioning — ensures a customer profile exists for an authenticated user,
 * OFF the request thread.
 * <p>
 * Authentication (issuing the JWT) must not wait on customer-service. Creating the customer
 * profile is a downstream side-effect, so it runs {@code @Async} on a dedicated pool and is
 * fail-open: if customer-service is slow or down, the auth response is unaffected and the
 * idempotent {@code /internal} endpoint reconciles the profile on the next register/login.
 * No message broker is involved — auth has no reason to depend on Kafka.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerProvisioning {

    private final CustomerRegistrationClient customerRegistrationClient;

    /**
     * Idempotently creates the customer profile for the given user. Safe to call from both
     * register() and login() — the {@code /internal} endpoint is a no-op when the profile
     * already exists. Fire-and-forget: never blocks or fails the calling auth flow.
     */
    @Async(AsyncConfig.AUTH_SIDE_EFFECTS_EXECUTOR)
    public void ensureCustomerProfile(User user) {
        try {
            customerRegistrationClient.createCustomer(
                    new CustomerRegistrationClient.CustomerRegistrationRequest(
                            String.valueOf(user.getId()),
                            user.getFirstname(),
                            user.getLastname(),
                            user.getEmail()));
            log.info("[CustomerProvisioning] Ensured customer profile for userId=[{}]", user.getId());
        } catch (Exception ex) {
            log.warn("[CustomerProvisioning] Customer profile provisioning failed for userId=[{}]: {}",
                    user.getId(), ex.getMessage());
        }
    }

    /**
     * Pushes the user's current display identity (name/email) to the customer profile,
     * OFF the request thread. Fail-open + idempotent: the internal endpoint no-ops when
     * the profile doesn't exist, and a missed sync self-heals on the next settings save.
     */
    @Async(AsyncConfig.AUTH_SIDE_EFFECTS_EXECUTOR)
    public void syncCustomerProfile(User user) {
        try {
            customerRegistrationClient.updateCustomer(
                    String.valueOf(user.getId()),
                    new CustomerRegistrationClient.CustomerRegistrationRequest(
                            String.valueOf(user.getId()),
                            user.getFirstname(),
                            user.getLastname(),
                            user.getEmail()));
            log.info("[CustomerProvisioning] Synced customer profile for userId=[{}]", user.getId());
        } catch (Exception ex) {
            log.warn("[CustomerProvisioning] Customer profile sync failed for userId=[{}]: {}",
                    user.getId(), ex.getMessage());
        }
    }

    /**
     * Deletes the customer profile after an account soft-delete, OFF the request thread.
     * Fail-open: the auth-side delete already succeeded; an orphaned profile is harmless
     * and the internal endpoint is idempotent (204 even when already gone).
     */
    @Async(AsyncConfig.AUTH_SIDE_EFFECTS_EXECUTOR)
    public void deleteCustomerProfile(Long userId) {
        try {
            customerRegistrationClient.deleteCustomer(String.valueOf(userId));
            log.info("[CustomerProvisioning] Deleted customer profile for userId=[{}]", userId);
        } catch (Exception ex) {
            log.warn("[CustomerProvisioning] Customer profile delete failed for userId=[{}]: {}",
                    userId, ex.getMessage());
        }
    }
}
