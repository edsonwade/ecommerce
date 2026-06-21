package code.with.vanilson.authentication.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * AsyncConfig — enables {@code @Async} and provides a dedicated executor for the
 * customer-profile side-effects of the auth flows.
 * <p>
 * Registration and login must return the JWT as fast as DB + password hashing allow.
 * Creating the customer profile (a call to customer-service) is NOT on that critical path,
 * so it runs on this small bounded pool instead of the request thread. The
 * {@link ThreadPoolExecutor.DiscardPolicy} means that under extreme back-pressure a
 * fail-open side-effect is simply dropped rather than ever blocking an auth response.
 * </p>
 *
 * @author vamuhong
 * @version 1.0
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String AUTH_SIDE_EFFECTS_EXECUTOR = "authSideEffectsExecutor";

    @Bean(AUTH_SIDE_EFFECTS_EXECUTOR)
    public Executor authSideEffectsExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("auth-sidefx-");
        // Never fall back to running the task on the caller (request) thread.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        executor.initialize();
        return executor;
    }
}
