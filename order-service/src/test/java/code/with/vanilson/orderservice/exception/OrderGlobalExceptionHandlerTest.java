package code.with.vanilson.orderservice.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.WebRequest;

import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for OrderGlobalExceptionHandler — BUG-005 sanitization.
 * Verifies that generic exception responses do NOT contain UUID references.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderGlobalExceptionHandler — BUG-005 Error Sanitization")
class OrderGlobalExceptionHandlerTest {

    @Mock
    private MessageSource messageSource;

    @Mock
    private WebRequest webRequest;

    @InjectMocks
    private OrderGlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        when(webRequest.getDescription(false)).thenReturn("uri=/api/v1/orders");
    }

    @Test
    @DisplayName("handleGenericException should return 500 with clean message — no UUID reference")
    void handleGenericException_shouldReturnCleanMessage() {
        // Given
        String cleanMessage = "An unexpected error occurred. Please try again later.";
        when(messageSource.getMessage(eq("order.error.internal.user"), isNull(), any(Locale.class)))
                .thenReturn(cleanMessage);

        Exception ex = new RuntimeException("NullPointerException in deep stack");

        // When
        ResponseEntity<Map<String, Object>> response = handler.handleGenericException(ex, webRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("message")).isEqualTo(cleanMessage);
        assertThat(body.get("errorCode")).isEqualTo("order.error.internal");
        assertThat(body.get("status")).isEqualTo(500);

        // Critical assertion: message must NOT contain any UUID pattern
        String msg = (String) body.get("message");
        assertThat(msg).doesNotContainPattern("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        assertThat(msg).doesNotContain("Reference:");
    }

    @Test
    @DisplayName("handleGenericException should use 'order.error.internal.user' key with null args — no UUID parameter")
    void handleGenericException_shouldUseUserMessageKey() {
        // Given
        when(messageSource.getMessage(eq("order.error.internal.user"), isNull(), any(Locale.class)))
                .thenReturn("An unexpected error occurred. Please try again later.");

        // When
        handler.handleGenericException(new RuntimeException("test"), webRequest);

        // Then — verify that the messageSource was called with the correct key and NO args
        verify(messageSource).getMessage(eq("order.error.internal.user"), isNull(), any(Locale.class));
    }

    @Test
    @DisplayName("handleGenericException response should follow consistent error structure")
    void handleGenericException_shouldFollowConsistentStructure() {
        // Given
        when(messageSource.getMessage(eq("order.error.internal.user"), isNull(), any(Locale.class)))
                .thenReturn("An unexpected error occurred. Please try again later.");

        // When
        ResponseEntity<Map<String, Object>> response =
                handler.handleGenericException(new RuntimeException("test"), webRequest);

        // Then — verify required fields
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body).containsKeys("timestamp", "status", "error", "errorCode", "message", "path");
        assertThat(body).doesNotContainKey("requestId");
        assertThat(body).doesNotContainKey("reference");
    }
}
