package code.with.vanilson.productservice.exception.handler;

import code.with.vanilson.productservice.exception.ProductBaseException;
import code.with.vanilson.productservice.exception.ProductNotFoundException;
import code.with.vanilson.productservice.exception.ProductNullException;
import code.with.vanilson.productservice.exception.ProductPurchaseException;
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
 * ProductGlobalExceptionHandler — Presentation Layer (Cross-cutting)
 * <p>
 * Centralised exception handler for the Product Service.
 * Open/Closed (SOLID-O): new exception types add new @ExceptionHandler methods — no edits to existing.
 * All messages resolved from messages.properties via MessageSource.
 * Stack traces are NEVER returned to the client.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
@Slf4j
@RestControllerAdvice
public class ProductGlobalExceptionHandler {

    private final MessageSource messageSource;

    public ProductGlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleProductNotFound(
            ProductNotFoundException ex, WebRequest request) {
        log.warn("[ProductExceptionHandler] Not found: key=[{}] msg=[{}]", ex.getMessageKey(), ex.getMessage());
        return buildResponse(ex.getHttpStatus(), ex.getMessage(), ex.getMessageKey(), request);
    }

    @ExceptionHandler(ProductNullException.class)
    public ResponseEntity<Map<String, Object>> handleProductNull(
            ProductNullException ex, WebRequest request) {
        log.warn("[ProductExceptionHandler] Null/invalid: key=[{}] msg=[{}]", ex.getMessageKey(), ex.getMessage());
        return buildResponse(ex.getHttpStatus(), ex.getMessage(), ex.getMessageKey(), request);
    }

    @ExceptionHandler(ProductPurchaseException.class)
    public ResponseEntity<Map<String, Object>> handleProductPurchase(
            ProductPurchaseException ex, WebRequest request) {
        log.warn("[ProductExceptionHandler] Purchase failed: key=[{}] msg=[{}]", ex.getMessageKey(), ex.getMessage());
        return buildResponse(ex.getHttpStatus(), ex.getMessage(), ex.getMessageKey(), request);
    }

    /** Catch-all for any remaining ProductBaseException subtype. */
    @ExceptionHandler(ProductBaseException.class)
    public ResponseEntity<Map<String, Object>> handleProductBase(
            ProductBaseException ex, WebRequest request) {
        log.error("[ProductExceptionHandler] Base exception: key=[{}] msg=[{}]", ex.getMessageKey(), ex.getMessage());
        return buildResponse(ex.getHttpStatus(), ex.getMessage(), ex.getMessageKey(), request);
    }

    /** Bean Validation (@Valid) field errors. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(
            MethodArgumentNotValidException ex, WebRequest request) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }
        log.warn("[ProductExceptionHandler] Validation failed: fields={}", fieldErrors.keySet());
        Map<String, Object> body = buildBase(HttpStatus.BAD_REQUEST,
                messageSource.getMessage("product.validation.failed", null, LocaleContextHolder.getLocale()),
                "product.validation.failed", request);
        body.put("fieldErrors", fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    /** Final safety net — never expose internal details. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex, WebRequest request) {
        String ref = UUID.randomUUID().toString();
        String message = messageSource.getMessage(
                "product.error.internal", new Object[]{ref}, LocaleContextHolder.getLocale());
        log.error("[ProductExceptionHandler] Unhandled exception ref=[{}]: {}", ref, ex.getMessage(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, message, "product.error.internal", request);
    }

    // -------------------------------------------------------

    private ResponseEntity<Map<String, Object>> buildResponse(
            HttpStatus status, String message, String errorCode, WebRequest request) {
        return ResponseEntity.status(status).body(buildBase(status, message, errorCode, request));
    }

    private Map<String, Object> buildBase(HttpStatus status, String message, String errorCode, WebRequest request) {
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
