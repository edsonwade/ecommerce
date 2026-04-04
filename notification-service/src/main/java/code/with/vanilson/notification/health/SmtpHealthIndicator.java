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
        try {
            mailSender.testConnection();
            return Health.up()
                    .withDetail("host", mailSender.getHost())
                    .withDetail("port", mailSender.getPort())
                    .build();
        } catch (Exception ex) {
            return Health.down()
                    .withDetail("host", mailSender.getHost())
                    .withDetail("port", mailSender.getPort())
                    .withDetail("error", ex.getMessage())
                    .build();
        }
    }
}
