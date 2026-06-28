package code.with.vanilson.authentication.infrastructure.email;

/**
 * EmailSender — outbound email seam (Dependency Inversion, SOLID-D).
 * <p>
 * The auth flows depend on this abstraction, never on a concrete transport. Today the only
 * implementation is {@link SmtpEmailSender} (JavaMailSender → MailHog in Docker). Swapping to a
 * real transactional provider (Resend / SendGrid / Mailgun) is a drop-in new {@code @Component}
 * selected by {@code app.email.provider} — no caller changes. That is intentional: the
 * provider-agnostic boundary is built now so the production-email upgrade is non-invasive.
 * </p>
 *
 * @author vamuhong
 * @version 1.0
 */
public interface EmailSender {

    /**
     * Sends the password-reset email. Implementations MUST be fail-open: a delivery failure is
     * logged, never thrown, so it cannot leak which addresses exist nor break the HTTP response.
     *
     * @param toEmail   recipient address
     * @param fullName  recipient display name (for the greeting)
     * @param resetLink absolute URL the user clicks to choose a new password
     */
    void sendPasswordResetEmail(String toEmail, String fullName, String resetLink);
}
