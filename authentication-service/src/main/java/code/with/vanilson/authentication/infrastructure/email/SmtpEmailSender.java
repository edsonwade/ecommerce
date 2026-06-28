package code.with.vanilson.authentication.infrastructure.email;

import code.with.vanilson.authentication.config.AsyncConfig;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * SmtpEmailSender — default {@link EmailSender} backed by {@link JavaMailSender}.
 * <p>
 * In this environment {@code spring.mail.*} points at MailHog ({@code mailhog:1025}, no auth),
 * so reset emails are captured in the MailHog UID (:8025) for inspection. Pointing the same env
 * vars at a real SMTP relay makes it send for real — and a future API-based provider is a new
 * {@code EmailSender} bean, leaving this one switched off via {@code app.email.provider}.
 * </p>
 * <p>
 * Runs on the bounded {@link AsyncConfig#AUTH_SIDE_EFFECTS_EXECUTOR} so SMTP latency never
 * touches the request thread, and is fail-open: any failure is logged, never thrown.
 * </p>
 *
 * @author vamuhong
 * @version 1.0
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.email.provider", havingValue = "smtp", matchIfMissing = true)
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;

    @Value("${app.email.from:noreply@vanilsonshop.io}")
    private String fromAddress;

    public SmtpEmailSender(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    @Async(AsyncConfig.AUTH_SIDE_EFFECTS_EXECUTOR)
    public void sendPasswordResetEmail(String toEmail, String fullName, String resetLink) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, UTF_8.name());
            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject("Reset your Obsidian Market password");
            helper.setText(buildHtml(fullName, resetLink), true);

            mailSender.send(message);
            log.info("[SmtpEmailSender] Password-reset email sent to=[{}]", toEmail);
        } catch (Exception ex) {
            // Fail-open: never surface delivery failures to the caller — doing so would both
            // leak which addresses exist and break the constant-200 forgot-password contract.
            log.error("[SmtpEmailSender] Failed to send password-reset email to=[{}]: {}",
                    toEmail, ex.getMessage());
        }
    }

    private String buildHtml(String fullName, String resetLink) {
        String name = (fullName == null || fullName.isBlank()) ? "there" : fullName;
        return """
               <div style="font-family:Arial,Helvetica,sans-serif;max-width:520px;margin:0 auto;color:#1a1a1a">
                 <h2 style="color:#7c3aed">Obsidian Market</h2>
                 <p>Hi %s,</p>
                 <p>We received a request to reset your password. Click the button below to choose a new one.
                    This link expires in 30 minutes and can be used once.</p>
                 <p style="text-align:center;margin:32px 0">
                   <a href="%s" style="background:#7c3aed;color:#fff;text-decoration:none;
                      padding:12px 28px;border-radius:8px;display:inline-block">Reset password</a>
                 </p>
                 <p style="font-size:13px;color:#555">If the button does not work, copy this link:<br>
                    <a href="%s">%s</a></p>
                 <p style="font-size:13px;color:#555">If you did not request this, you can safely ignore this email —
                    your password will not change.</p>
               </div>
               """.formatted(name, resetLink, resetLink, resetLink);
    }
}
