package code.with.vanilson.notification.health;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

/**
 * SMTP liveness indicator.
 * <p>
 * DISABLED by default: {@link JavaMailSenderImpl#testConnection()} performs a full SMTP
 * AUTH against the relay on <em>every</em> actuator health poll (~every 18s). Against a
 * rate-limited sandbox relay (Mailtrap) this floods the login endpoint and trips a
 * "535 Too many failed login attempts" lock, which then blocks genuine password-reset
 * sends indefinitely. Mail delivery is already fail-open in the senders, so its health is
 * not request-critical. Enable only with a real, non-rate-limited relay by setting
 * {@code management.health.smtp.enabled=true}.
 */
@Component
@ConditionalOnProperty(name = "management.health.smtp.enabled", havingValue = "true")
@RequiredArgsConstructor
public class SmtpHealthIndicator implements HealthIndicator {

    private final JavaMailSenderImpl mailSender;

    @Override
    public Health health() {
        String host = mailSender.getHost() != null ? mailSender.getHost() : "unknown";
        int port = mailSender.getPort();
        try {
            mailSender.testConnection();
            return Health.up()
                    .withDetail("host", host)
                    .withDetail("port", port)
                    .build();
        } catch (Exception ex) {
            String error = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            return Health.down()
                    .withDetail("host", host)
                    .withDetail("port", port)
                    .withDetail("error", error)
                    .build();
        }
    }
}
