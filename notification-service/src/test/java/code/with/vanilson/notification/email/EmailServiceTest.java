package code.with.vanilson.notification.email;

import code.with.vanilson.notification.kafka.order.OrderConfirmation;
import code.with.vanilson.notification.pdf.InvoicePdfService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmailService — Unit Tests")
class EmailServiceTest {

    @Mock private JavaMailSender mailSender;
    @Mock private SpringTemplateEngine templateEngine;
    @Mock private InvoicePdfService invoicePdfService;
    @Mock private MessageSource messageSource;

    @Mock private MimeMessage mimeMessage;

    @InjectMocks
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        lenient().when(messageSource.getMessage(anyString(), any(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("should send payment success email successfully")
    void shouldSendPaymentSuccessEmail() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(anyString(), any(Context.class))).thenReturn("<html>Payment OK</html>");

        emailService.sendPaymentSuccessEmail("test@example.com", "John Doe", BigDecimal.TEN, "REF-001");

        verify(mailSender).send(mimeMessage);
        verify(templateEngine).process(eq("payment-confirmation.html"), any(Context.class));
    }

    @Test
    @DisplayName("should catch MessagingException when sending payment email fails")
    void shouldCatchMessagingExceptionOnPaymentEmail() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(anyString(), any(Context.class))).thenReturn("<html>Payment OK</html>");
        doThrow(new org.springframework.mail.MailSendException("Simulated error"))
                .when(mailSender).send(any(MimeMessage.class));

        // Should not throw exception upwards because it's caught
        emailService.sendPaymentSuccessEmail("test@example.com", "John Doe", BigDecimal.TEN, "REF-001");

        verify(mailSender).send(mimeMessage);
    }

    @Test
    @DisplayName("should send order confirmation email with PDF invoice attached")
    void shouldSendOrderConfirmationEmail() throws Exception {
        OrderConfirmation.CustomerSummary customer = new OrderConfirmation.CustomerSummary("c-1", "John", "Doe", "test@example.com");
        OrderConfirmation.ProductSummary product = new OrderConfirmation.ProductSummary(1, "Prod", "Desc", BigDecimal.TEN, 1.0);
        OrderConfirmation confirmation = new OrderConfirmation(
                "REF-001", BigDecimal.TEN, "CREDIT_CARD", customer, List.of(product));

        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(anyString(), any(Context.class))).thenReturn("<html>Order OK</html>");
        when(invoicePdfService.generateInvoice(confirmation)).thenReturn(new byte[]{1, 2, 3});

        emailService.sendOrderConfirmationEmail("test@example.com", "John Doe", BigDecimal.TEN, "REF-001", confirmation);

        verify(mailSender).send(mimeMessage);
        verify(templateEngine).process(eq("order-confirmation.html"), any(Context.class));
        verify(invoicePdfService).generateInvoice(confirmation);
    }
}
