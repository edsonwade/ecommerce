package code.with.vanilson.paymentservice.presentation;

import code.with.vanilson.paymentservice.application.PaymentRequest;
import code.with.vanilson.paymentservice.application.PaymentResponse;
import code.with.vanilson.paymentservice.application.PaymentService;
import code.with.vanilson.paymentservice.domain.CustomerData;
import code.with.vanilson.paymentservice.domain.PaymentMethod;
import code.with.vanilson.paymentservice.exception.PaymentNotFoundException;
import code.with.vanilson.tenantcontext.TenantHibernateFilterActivator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PaymentControllerTest — Web Layer (Slice) Tests
 * <p>
 * Uses @WebMvcTest to load only the MVC slice: controller + exception handler.
 * No database, no Kafka, no full application context — tests the HTTP contract only.
 * <p>
 * Covers:
 * - POST /api/v1/payments  → 201 Created | 400 Bad Request (field validation)
 * - GET  /api/v1/payments  → 200 OK (list | empty)
 * - GET  /api/v1/payments/{id} → 200 OK | 404 Not Found (structured error body)
 * </p>
 *
 * @author vamuhong
 * @version 1.0
 */
@WebMvcTest(PaymentController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("PaymentController — Web Layer Tests")
class PaymentControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    PaymentService paymentService;

    @SuppressWarnings("unused")
    @MockBean
    TenantHibernateFilterActivator activator;

    /**
     * MessageSource is required by PaymentGlobalExceptionHandler (loaded by @WebMvcTest).
     * We mock it so the fallback generic-error handler can resolve messages without
     * a real ResourceBundle on the test classpath.
     */
    @MockBean
    MessageSource messageSource;

    private PaymentRequest validRequest;
    private PaymentResponse paymentResponse;

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

        paymentResponse = new PaymentResponse(
                1,
                BigDecimal.valueOf(199.99),
                "CREDIT_CARD",
                42,
                "ORD-2024-001",
                LocalDateTime.of(2024, 1, 15, 10, 30)
        );

        // Return the message key itself — keeps assertions decoupled from actual message text.
        when(messageSource.getMessage(any(String.class), any(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // -------------------------------------------------------
    // POST /api/v1/payments
    // -------------------------------------------------------

    @Nested
    @DisplayName("POST /api/v1/payments")
    class CreatePayment {

        @Test
        @DisplayName("should return 201 Created with payment ID when request is valid")
        void shouldReturn201WithPaymentIdOnValidRequest() throws Exception {
            when(paymentService.createPayment(any(PaymentRequest.class))).thenReturn(1);

            mockMvc.perform(post("/api/v1/payments")
                            .header("X-Tenant-ID", "test-tenant-123")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isCreated())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").value(1));
        }

        @Test
        @DisplayName("should return 400 Bad Request when amount is null")
        void shouldReturn400WhenAmountIsNull() throws Exception {
            PaymentRequest request = new PaymentRequest(
                    null, null, PaymentMethod.CREDIT_CARD, 42, "ORD-001",
                    new CustomerData("c-1", "Ana", "Silva", "ana@example.com")
            );

            mockMvc.perform(post("/api/v1/payments")
                            .header("X-Tenant-ID", "test-tenant-123")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.amount").exists());
        }

        @Test
        @DisplayName("should return 400 Bad Request when amount is negative")
        void shouldReturn400WhenAmountIsNegative() throws Exception {
            PaymentRequest request = new PaymentRequest(
                    null, BigDecimal.valueOf(-1), PaymentMethod.CREDIT_CARD, 42, "ORD-001",
                    new CustomerData("c-1", "Ana", "Silva", "ana@example.com")
            );

            mockMvc.perform(post("/api/v1/payments")
                            .header("X-Tenant-ID", "test-tenant-123")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.amount").exists());
        }

        @Test
        @DisplayName("should return 400 Bad Request when paymentMethod is null")
        void shouldReturn400WhenPaymentMethodIsNull() throws Exception {
            PaymentRequest request = new PaymentRequest(
                    null, BigDecimal.valueOf(100), null, 42, "ORD-001",
                    new CustomerData("c-1", "Ana", "Silva", "ana@example.com")
            );

            mockMvc.perform(post("/api/v1/payments")
                            .header("X-Tenant-ID", "test-tenant-123")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.paymentMethod").exists());
        }

        @Test
        @DisplayName("should return 400 Bad Request when orderReference is blank")
        void shouldReturn400WhenOrderReferenceIsBlank() throws Exception {
            PaymentRequest request = new PaymentRequest(
                    null, BigDecimal.valueOf(100), PaymentMethod.PAYPAL, 42, "",
                    new CustomerData("c-1", "Ana", "Silva", "ana@example.com")
            );

            mockMvc.perform(post("/api/v1/payments")
                            .header("X-Tenant-ID", "test-tenant-123")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.orderReference").exists());
        }

        @Test
        @DisplayName("should return 400 Bad Request when orderId is null")
        void shouldReturn400WhenOrderIdIsNull() throws Exception {
            PaymentRequest request = new PaymentRequest(
                    null, BigDecimal.valueOf(100), PaymentMethod.VISA, null, "ORD-001",
                    new CustomerData("c-1", "Ana", "Silva", "ana@example.com")
            );

            mockMvc.perform(post("/api/v1/payments")
                            .header("X-Tenant-ID", "test-tenant-123")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.orderId").exists());
        }

        @Test
        @DisplayName("should return 400 Bad Request when customer is null")
        void shouldReturn400WhenCustomerIsNull() throws Exception {
            PaymentRequest request = new PaymentRequest(
                    null, BigDecimal.valueOf(100), PaymentMethod.MASTER_CARD, 42, "ORD-001", null
            );

            mockMvc.perform(post("/api/v1/payments")
                            .header("X-Tenant-ID", "test-tenant-123")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.customer").exists());
        }

        @Test
        @DisplayName("should return 400 when body is missing entirely")
        void shouldReturn400WhenBodyIsMissing() throws Exception {
            mockMvc.perform(post("/api/v1/payments")
                            .header("X-Tenant-ID", "test-tenant-123")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
        }
    }

    // -------------------------------------------------------
    // GET /api/v1/payments
    // -------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/payments")
    class FindAll {

        @Test
        @DisplayName("should return 200 OK with list of payment responses")
        void shouldReturn200WithPaymentList() throws Exception {
            when(paymentService.findAllPayments()).thenReturn(List.of(paymentResponse));

            mockMvc.perform(get("/api/v1/payments")
                            .header("X-Tenant-ID", "test-tenant-123"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].paymentId").value(1))
                    .andExpect(jsonPath("$[0].orderReference").value("ORD-2024-001"))
                    .andExpect(jsonPath("$[0].paymentMethod").value("CREDIT_CARD"))
                    .andExpect(jsonPath("$[0].amount").value(199.99))
                    .andExpect(jsonPath("$[0].orderId").value(42));
        }

        @Test
        @DisplayName("should return 200 OK with empty array when no payments exist")
        void shouldReturn200WithEmptyArrayWhenNoPayments() throws Exception {
            when(paymentService.findAllPayments()).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/payments")
                            .header("X-Tenant-ID", "test-tenant-123"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$", hasSize(0)));
        }
    }

    // -------------------------------------------------------
    // GET /api/v1/payments/{payment-id}
    // -------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/payments/{payment-id}")
    class FindById {

        @Test
        @DisplayName("should return 200 OK with full payment response when payment exists")
        void shouldReturn200WithPaymentWhenFound() throws Exception {
            when(paymentService.findById(1)).thenReturn(paymentResponse);

            mockMvc.perform(get("/api/v1/payments/1")
                            .header("X-Tenant-ID", "test-tenant-123"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.paymentId").value(1))
                    .andExpect(jsonPath("$.amount").value(199.99))
                    .andExpect(jsonPath("$.paymentMethod").value("CREDIT_CARD"))
                    .andExpect(jsonPath("$.orderId").value(42))
                    .andExpect(jsonPath("$.orderReference").value("ORD-2024-001"));
        }

        @Test
        @DisplayName("should return 404 Not Found with structured error body when payment does not exist")
        void shouldReturn404WhenPaymentNotFound() throws Exception {
            when(paymentService.findById(999))
                    .thenThrow(new PaymentNotFoundException("Payment 999 not found", "payment.not.found"));

            mockMvc.perform(get("/api/v1/payments/999")
                            .header("X-Tenant-ID", "test-tenant-123"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.error").value("Not Found"))
                    .andExpect(jsonPath("$.errorCode").value("payment.not.found"))
                    .andExpect(jsonPath("$.message").value("Payment 999 not found"))
                    .andExpect(jsonPath("$.path").value("/api/v1/payments/999"));
        }

        @Test
        @DisplayName("should return 404 error body with timestamp field")
        void shouldIncludeTimestampIn404ErrorBody() throws Exception {
            when(paymentService.findById(99))
                    .thenThrow(new PaymentNotFoundException("Not found", "payment.not.found"));

            mockMvc.perform(get("/api/v1/payments/99")
                            .header("X-Tenant-ID", "test-tenant-123"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.status").value(404));
        }
    }
}
