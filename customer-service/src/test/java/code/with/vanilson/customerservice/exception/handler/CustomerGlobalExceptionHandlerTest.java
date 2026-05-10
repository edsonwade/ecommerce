package code.with.vanilson.customerservice.exception.handler;

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
 * Unit test for CustomerGlobalExceptionHandler — BUG-005 sanitization.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CustomerGlobalExceptionHandler — BUG-005 Error Sanitization")
class CustomerGlobalExceptionHandlerTest {

    @Mock
    private MessageSource messageSource;

    @Mock
    private WebRequest webRequest;

    @InjectMocks
    private CustomerGlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        when(webRequest.getDescription(false)).thenReturn("uri=/api/v1/customers");
    }

    @Test
    @DisplayName("handleGeneric should return 500 with clean message — no UUID reference")
    void handleGeneric_shouldReturnCleanMessage() {
        String cleanMessage = "An unexpected error occurred. Please try again later.";
        when(messageSource.getMessage(eq("customer.error.internal.user"), isNull(), any(Locale.class)))
                .thenReturn(cleanMessage);

        ResponseEntity<Map<String, Object>> response =
                handler.handleGeneric(new RuntimeException("test"), webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("message")).isEqualTo(cleanMessage);
        assertThat(body.get("errorCode")).isEqualTo("customer.error.internal");

        String msg = (String) body.get("message");
        assertThat(msg).doesNotContainPattern("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        assertThat(msg).doesNotContain("Reference:");
    }

    @Test
    @DisplayName("handleGeneric should use 'customer.error.internal.user' key with null args")
    void handleGeneric_shouldUseUserMessageKey() {
        when(messageSource.getMessage(eq("customer.error.internal.user"), isNull(), any(Locale.class)))
                .thenReturn("An unexpected error occurred. Please try again later.");

        handler.handleGeneric(new RuntimeException("test"), webRequest);

        verify(messageSource).getMessage(eq("customer.error.internal.user"), isNull(), any(Locale.class));
    }

    @Test
    @DisplayName("handleGeneric response should follow consistent error structure")
    void handleGeneric_shouldFollowConsistentStructure() {
        when(messageSource.getMessage(eq("customer.error.internal.user"), isNull(), any(Locale.class)))
                .thenReturn("An unexpected error occurred. Please try again later.");

        ResponseEntity<Map<String, Object>> response =
                handler.handleGeneric(new RuntimeException("test"), webRequest);

        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body).containsKeys("timestamp", "status", "error", "errorCode", "message", "path");
        assertThat(body).doesNotContainKey("requestId");
        assertThat(body).doesNotContainKey("reference");
    }
}
