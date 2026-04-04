package code.with.vanilson.notification.health;

import jakarta.mail.MessagingException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SmtpHealthIndicatorTest {

    @Test
    void health_isUp_whenSmtpTestConnectionSucceeds() throws Exception {
        JavaMailSenderImpl sender = mock(JavaMailSenderImpl.class);
        when(sender.getHost()).thenReturn("smtp.gmail.com");
        when(sender.getPort()).thenReturn(587);
        doNothing().when(sender).testConnection();

        SmtpHealthIndicator indicator = new SmtpHealthIndicator(sender);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsKey("host");
        assertThat(health.getDetails()).containsKey("port");
    }

    @Test
    void health_isDown_whenSmtpTestConnectionFails() throws Exception {
        JavaMailSenderImpl sender = mock(JavaMailSenderImpl.class);
        when(sender.getHost()).thenReturn("smtp.gmail.com");
        when(sender.getPort()).thenReturn(587);
        doThrow(new MessagingException("Connection refused")).when(sender).testConnection();

        SmtpHealthIndicator indicator = new SmtpHealthIndicator(sender);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails().get("error").toString()).contains("refused");
    }
}
