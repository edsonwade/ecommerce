package code.with.vanilson.paymentservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * PaymentGlobalExceptionHandler — Presentation Layer (Cross-cutting concern)
 * <p>
 * Centralised exception handler for the Payment Service.
 * Open/Closed Principle (SOLID-O): adding a new exception type requires only
 * adding a new @ExceptionHandler method — no modification of existing handlers.
 * <p>
 * All user-facing messages are resolved from messages.properties via MessageSource.
 * Raw Java exception messages are NEVER returned to the client.
 * Stack traces are logged server-side only.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
@Slf4j
@RestControllerAdvice
public class PaymentGlobalExceptionHandler {

    private final MessageSource messageSource;

    public PaymentGlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handlePaymentNotFound(
            PaymentNotFoundException ex, WebRequest request) {
        log.warn("[PaymentExceptionHandler] Not found: key=[{}] message=[{}]",
                ex.getMessageKey(), ex.getMessage());
        return buildResponse(ex.getHttpStatus(), ex.getMessage(), ex.getMessageKey(), request);
    }

    @ExceptionHandler(PaymentValidationException.class)
    public ResponseEntity<Map<String, Object>> handlePaymentValidation(
            PaymentValidationException ex, WebRequest request) {
        log.warn("[PaymentExceptionHandler] Validation error: key=[{}] message=[{}]",
                ex.getMessageKey(), ex.getMessage());
        return buildResponse(ex.getHttpStatus(), ex.getMessage(), ex.getMessageKey(), request);
    }

    @ExceptionHandler(DuplicatePaymentException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicatePayment(
            DuplicatePaymentException ex, WebRequest request) {
        log.warn("[PaymentExceptionHandler] Duplicate payment: key=[{}] message=[{}]",
                ex.getMessageKey(), ex.getMessage());
        return buildResponse(ex.getHttpStatus(), ex.getMessage(), ex.getMessageKey(), request);
    }

    /**
     * Handles Bean Validation errors (@Valid on request bodies).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(
            MethodArgumentNotValidException ex, WebRequest request) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }
        log.warn("[PaymentExceptionHandler] Bean validation failed: fields={}", fieldErrors.keySet());
        Map<String, Object> body = buildBaseBody(HttpStatus.BAD_REQUEST,
                "Dados inválidos na requisição.", "payment.validation.failed", request);
        body.put("fieldErrors", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * Fallback — never exposes internal details or stack traces to the client.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(
            Exception ex, WebRequest request) {
        String ref = UUID.randomUUID().toString();
        String message = messageSource.getMessage(
                "payment.error.internal",
                new Object[]{ref},
                LocaleContextHolder.getLocale());
        log.error("[PaymentExceptionHandler] Unhandled exception ref=[{}]: {}", ref, ex.getMessage(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, message, "payment.error.internal", request);
    }

    // -------------------------------------------------------

    private ResponseEntity<Map<String, Object>> buildResponse(
            HttpStatus status, String message, String errorCode, WebRequest request) {
        return ResponseEntity.status(status).body(buildBaseBody(status, message, errorCode, request));
    }

    private Map<String, Object> buildBaseBody(
            HttpStatus status, String message, String errorCode, WebRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("errorCode", errorCode);
        body.put("message", message);
        body.put("path", request.getDescription(false).replace("uri=", ""));
        return body;
    }
}
