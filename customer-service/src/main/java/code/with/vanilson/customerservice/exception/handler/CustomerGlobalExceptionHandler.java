package code.with.vanilson.customerservice.exception.handler;

import code.with.vanilson.customerservice.exception.CustomerBaseException;
import code.with.vanilson.customerservice.exception.CustomerNotFoundException;
import code.with.vanilson.customerservice.exception.EmailAlreadyExistsException;
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
 * CustomerGlobalExceptionHandler — Presentation Layer (Cross-cutting)
 * <p>
 * Handles all exceptions from Customer Service controllers.
 * Open/Closed (SOLID-O): new exception types → new handler method, zero edits to existing ones.
 * Dependency Inversion (SOLID-D): depends on MessageSource abstraction.
 * All messages from messages.properties — no hardcoded strings in handlers.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
@Slf4j
@RestControllerAdvice
public class CustomerGlobalExceptionHandler {

    private final MessageSource messageSource;

    public CustomerGlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(CustomerNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(
            CustomerNotFoundException ex, WebRequest request) {
        log.warn("[CustomerExceptionHandler] Not found: key=[{}] msg=[{}]",
                ex.getMessageKey(), ex.getMessage());
        return build(ex.getHttpStatus(), ex.getMessage(), ex.getMessageKey(), request);
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> handleEmailDuplicate(
            EmailAlreadyExistsException ex, WebRequest request) {
        log.warn("[CustomerExceptionHandler] Email duplicate: key=[{}] msg=[{}]",
                ex.getMessageKey(), ex.getMessage());
        return build(ex.getHttpStatus(), ex.getMessage(), ex.getMessageKey(), request);
    }

    @ExceptionHandler(CustomerBaseException.class)
    public ResponseEntity<Map<String, Object>> handleCustomerBase(
            CustomerBaseException ex, WebRequest request) {
        log.error("[CustomerExceptionHandler] Base exception: key=[{}]", ex.getMessageKey(), ex);
        return build(ex.getHttpStatus(), ex.getMessage(), ex.getMessageKey(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException ex, WebRequest request) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }
        log.warn("[CustomerExceptionHandler] Validation failed: {}", fieldErrors.keySet());
        Map<String, Object> body = buildBase(HttpStatus.BAD_REQUEST,
                messageSource.getMessage("customer.validation.failed", null,
                        LocaleContextHolder.getLocale()),
                "customer.validation.failed", request);
        body.put("fieldErrors", fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(org.springframework.web.bind.MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParam(
            org.springframework.web.bind.MissingServletRequestParameterException ex, WebRequest request) {
        log.warn("[CustomerExceptionHandler] Missing parameter: {}", ex.getParameterName());
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), "customer.validation.missing_param", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex, WebRequest request) {
        String ref = UUID.randomUUID().toString();
        String msg = messageSource.getMessage("customer.error.internal",
                new Object[]{ref}, LocaleContextHolder.getLocale());
        log.error("[CustomerExceptionHandler] Unhandled ref=[{}]: {}", ref, ex.getMessage(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, msg, "customer.error.internal", request);
    }

    // -------------------------------------------------------
    private ResponseEntity<Map<String, Object>> build(
            HttpStatus status, String message, String code, WebRequest request) {
        return ResponseEntity.status(status).body(buildBase(status, message, code, request));
    }

    private Map<String, Object> buildBase(
            HttpStatus status, String message, String code, WebRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("errorCode", code);
        body.put("message", message);
        body.put("path", request.getDescription(false).replace("uri=", ""));
        return body;
    }
}
