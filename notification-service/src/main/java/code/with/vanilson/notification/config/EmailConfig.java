package code.with.vanilson.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

@Component
public class EmailConfig {
    @Bean
    public JavaMailSender mailSender() {
        return new JavaMailSenderImpl();
    }
}
