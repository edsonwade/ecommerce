package code.with.vanilson.paymentservice;

import code.with.vanilson.paymentservice.application.PaymentMapper;
import code.with.vanilson.paymentservice.application.PaymentRequest;
import code.with.vanilson.paymentservice.application.PaymentResponse;
import code.with.vanilson.paymentservice.application.PaymentService;
import code.with.vanilson.paymentservice.domain.CustomerData;
import code.with.vanilson.paymentservice.domain.Payment;
import code.with.vanilson.paymentservice.domain.PaymentMethod;
import code.with.vanilson.paymentservice.exception.PaymentNotFoundException;
import code.with.vanilson.paymentservice.infrastructure.messaging.NotificationProducer;
import code.with.vanilson.paymentservice.infrastructure.messaging.PaymentNotificationRequest;
import code.with.vanilson.paymentservice.infrastructure.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PaymentServiceTest — Unit Tests
 * <p>
 * Covers the critical idempotency contract:
 * - First call → persists payment + publishes Kafka notification
 * - Duplicate call (same orderReference) → returns existing payment, NO double persist, NO double notify
 * <p>
 * Framework: JUnit 5 + Mockito + AssertJ (no hardcoded assertion messages).
 * Pattern: Nested @Nested classes group related scenarios for readability.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService — Unit Tests")
class PaymentServiceTest {

    @Mock private PaymentRepository   paymentRepository;
    @Mock private PaymentMapper       paymentMapper;
    @Mock private NotificationProducer notificationProducer;
    @Mock private MessageSource       messageSource;

    @InjectMocks
    private PaymentService paymentService;

    // -------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------

    private PaymentRequest validRequest;
    private Payment        savedPayment;
    private PaymentResponse savedResponse;

    @BeforeEach
    void setUp() {
        CustomerData customer = new CustomerData("c-001", "Ana", "Silva", "ana@example.com");

        validRequest = new PaymentRequest(
                null,
                BigDecimal.valueOf(199.99),
                PaymentMethod.CREDIT_CARD,
                42,
                "ORD-2024-001",
                customer
        );

        savedPayment = Payment.builder()
                .paymentId(1)
                .amount(BigDecimal.valueOf(199.99))
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .orderId(42)
                .orderReference("ORD-2024-001")
                .idempotencyKey("payment:ORD-2024-001")
                .createdDate(LocalDateTime.now())
                .build();

        savedResponse = new PaymentResponse(
                1, BigDecimal.valueOf(199.99), "CREDIT_CARD",
                42, "ORD-2024-001", LocalDateTime.now());

        // MessageSource: return key itself so we can check calls without exact message strings
        when(messageSource.getMessage(anyString(), any(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // -------------------------------------------------------
    // createPayment — first occurrence
    // -------------------------------------------------------

    @Nested
    @DisplayName("createPayment — first occurrence (no duplicate)")
    class FirstOccurrence {

        @Test
        @DisplayName("should persist payment and publish notification when no duplicate exists")
        void shouldPersistAndPublishOnFirstCall() {
            // GIVEN — no existing payment for this idempotency key
            when(paymentRepository.findByIdempotencyKey("payment:ORD-2024-001"))
                    .thenReturn(Optional.empty());
            when(paymentMapper.toPayment(validRequest)).thenReturn(savedPayment);
            when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);

            // WHEN
            Integer result = paymentService.createPayment(validRequest);

            // THEN — payment was persisted exactly once
            assertThat(result)
                    .as("Returned payment ID should match the saved payment")
                    .isEqualTo(1);

            verify(paymentRepository, times(1)).save(any(Payment.class));
            verify(notificationProducer, times(1)).sendNotification(any(PaymentNotificationRequest.class));
        }

        @Test
        @DisplayName("should set idempotency key on the Payment entity before saving")
        void shouldSetIdempotencyKeyBeforeSave() {
            when(paymentRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
            when(paymentMapper.toPayment(validRequest)).thenReturn(savedPayment);
            when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);

            paymentService.createPayment(validRequest);

            // Capture what was actually saved
            ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepository).save(captor.capture());

            assertThat(captor.getValue().getIdempotencyKey())
                    .as("Idempotency key must be set to 'payment:<orderReference>'")
                    .isEqualTo("payment:ORD-2024-001");
        }

        @Test
        @DisplayName("should send notification with correct customer email")
        void shouldSendNotificationWithCorrectEmail() {
            when(paymentRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
            when(paymentMapper.toPayment(validRequest)).thenReturn(savedPayment);
            when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);

            paymentService.createPayment(validRequest);

            ArgumentCaptor<PaymentNotificationRequest> captor =
                    ArgumentCaptor.forClass(PaymentNotificationRequest.class);
            verify(notificationProducer).sendNotification(captor.capture());

            assertThat(captor.getValue().customerEmail())
                    .as("Notification must target the correct customer email")
                    .isEqualTo("ana@example.com");
            assertThat(captor.getValue().orderReference())
                    .as("Notification must carry the correct order reference")
                    .isEqualTo("ORD-2024-001");
        }
    }

    // -------------------------------------------------------
    // createPayment — duplicate detection (idempotency)
    // -------------------------------------------------------

    @Nested
    @DisplayName("createPayment — duplicate (idempotency guard)")
    class DuplicateDetection {

        @Test
        @DisplayName("should return existing payment ID without saving again on duplicate call")
        void shouldReturnExistingPaymentWithoutDoubleCharge() {
            // GIVEN — payment already exists for this idempotency key
            when(paymentRepository.findByIdempotencyKey("payment:ORD-2024-001"))
                    .thenReturn(Optional.of(savedPayment));

            // WHEN — same request arrives again (network retry / duplicate call)
            Integer result = paymentService.createPayment(validRequest);

            // THEN — no new payment persisted, no notification sent
            assertThat(result)
                    .as("Should return existing payment ID, not create a new one")
                    .isEqualTo(1);

            verify(paymentRepository, never()).save(any(Payment.class));
            verify(notificationProducer, never()).sendNotification(any());
        }

        @Test
        @DisplayName("should not call mapper on duplicate — no entity creation")
        void shouldNotCallMapperOnDuplicate() {
            when(paymentRepository.findByIdempotencyKey(anyString()))
                    .thenReturn(Optional.of(savedPayment));

            paymentService.createPayment(validRequest);

            verify(paymentMapper, never()).toPayment(any());
        }
    }

    // -------------------------------------------------------
    // findById
    // -------------------------------------------------------

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("should return PaymentResponse when payment exists")
        void shouldReturnPaymentWhenFound() {
            when(paymentRepository.findById(1)).thenReturn(Optional.of(savedPayment));
            when(paymentMapper.toResponse(savedPayment)).thenReturn(savedResponse);

            PaymentResponse result = paymentService.findById(1);

            assertThat(result)
                    .as("Response must not be null")
                    .isNotNull();
            assertThat(result.paymentId())
                    .as("Response payment ID must match")
                    .isEqualTo(1);
            assertThat(result.orderReference())
                    .as("Response order reference must match")
                    .isEqualTo("ORD-2024-001");
        }

        @Test
        @DisplayName("should throw PaymentNotFoundException when payment does not exist")
        void shouldThrowNotFoundWhenPaymentMissing() {
            when(paymentRepository.findById(99)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.findById(99))
                    .as("Should throw PaymentNotFoundException for unknown ID")
                    .isInstanceOf(PaymentNotFoundException.class);
        }
    }

    // -------------------------------------------------------
    // findAllPayments
    // -------------------------------------------------------

    @Nested
    @DisplayName("findAllPayments")
    class FindAll {

        @Test
        @DisplayName("should return list of all payments mapped to responses")
        void shouldReturnAllPayments() {
            when(paymentRepository.findAll()).thenReturn(List.of(savedPayment));
            when(paymentMapper.toResponse(savedPayment)).thenReturn(savedResponse);

            List<PaymentResponse> results = paymentService.findAllPayments();

            assertThat(results)
                    .as("Result list must not be null or empty")
                    .isNotNull()
                    .hasSize(1);
            assertThat(results.get(0).orderReference())
                    .as("First result must have correct order reference")
                    .isEqualTo("ORD-2024-001");
        }

        @Test
        @DisplayName("should return empty list when no payments exist")
        void shouldReturnEmptyListWhenNoPayments() {
            when(paymentRepository.findAll()).thenReturn(List.of());

            List<PaymentResponse> results = paymentService.findAllPayments();

            assertThat(results)
                    .as("Result must be an empty list, not null")
                    .isNotNull()
                    .isEmpty();
        }
    }
}
