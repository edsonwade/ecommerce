package code.with.vanilson.notification.health;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

@Component
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
