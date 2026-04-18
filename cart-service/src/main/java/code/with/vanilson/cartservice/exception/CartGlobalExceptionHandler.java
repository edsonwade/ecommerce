package code.with.vanilson.cartservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.context.request.WebRequest;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * CartGlobalExceptionHandler — Presentation Layer (Cross-cutting)
 * <p>
 * Open/Closed (SOLID-O): new exception types → new handler, no edits to existing ones.
 * All messages from messages.properties — zero hardcoded strings.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
@Slf4j
@RestControllerAdvice
public class CartGlobalExceptionHandler {

    private final MessageSource messageSource;

    public CartGlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(CartForbiddenException.class)
    public ResponseEntity<Map<String, Object>> handleForbidden(CartForbiddenException ex, WebRequest req) {
        log.warn("[CartHandler] Forbidden: key=[{}]", ex.getMessageKey());
        return build(ex.getHttpStatus(), ex.getMessage(), ex.getMessageKey(), req);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex, WebRequest req) {
        String msg = messageSource.getMessage("cart.access.denied", null, LocaleContextHolder.getLocale());
        log.warn("[CartHandler] Access denied: {}", ex.getMessage());
        return build(HttpStatus.FORBIDDEN, msg, "cart.access.denied", req);
    }

    @ExceptionHandler(CartNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(CartNotFoundException ex, WebRequest req) {
        log.warn("[CartHandler] Not found: key=[{}]", ex.getMessageKey());
        return build(ex.getHttpStatus(), ex.getMessage(), ex.getMessageKey(), req);
    }

    @ExceptionHandler(CartValidationException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(CartValidationException ex, WebRequest req) {
        log.warn("[CartHandler] Validation: key=[{}]", ex.getMessageKey());
        return build(ex.getHttpStatus(), ex.getMessage(), ex.getMessageKey(), req);
    }

    @ExceptionHandler(CartBaseException.class)
    public ResponseEntity<Map<String, Object>> handleBase(CartBaseException ex, WebRequest req) {
        log.error("[CartHandler] Base exception: key=[{}]", ex.getMessageKey(), ex);
        return build(ex.getHttpStatus(), ex.getMessage(), ex.getMessageKey(), req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleBeanValidation(
            MethodArgumentNotValidException ex, WebRequest req) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }
        log.warn("[CartHandler] Bean validation: {}", fieldErrors.keySet());
        Map<String, Object> body = base(HttpStatus.BAD_REQUEST,
                messageSource.getMessage("cart.validation.failed", null, LocaleContextHolder.getLocale()),
                "cart.validation.failed", req);
        body.put("fieldErrors", fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Map<String, Object>> handleMethodValidation(
            HandlerMethodValidationException ex, WebRequest req) {
        log.warn("[CartHandler] Method validation failed: {}", ex.getMessage());
        Map<String, Object> body = base(HttpStatus.BAD_REQUEST,
                messageSource.getMessage("cart.validation.failed", null, LocaleContextHolder.getLocale()),
                "cart.validation.failed", req);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex, WebRequest req) {
        String ref = UUID.randomUUID().toString();
        String msg = messageSource.getMessage("cart.error.internal",
                new Object[]{ref}, LocaleContextHolder.getLocale());
        log.error("[CartHandler] Unhandled ref=[{}]: {}", ref, ex.getMessage(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, msg, "cart.error.internal", req);
    }

    private ResponseEntity<Map<String, Object>> build(
            HttpStatus s, String m, String code, WebRequest req) {
        return ResponseEntity.status(s).body(base(s, m, code, req));
    }

    private Map<String, Object> base(HttpStatus s, String m, String code, WebRequest req) {
        Map<String, Object> b = new HashMap<>();
        b.put("timestamp", Instant.now().toString());
        b.put("status",    s.value());
        b.put("error",     s.getReasonPhrase());
        b.put("errorCode", code);
        b.put("message",   m);
        b.put("path",      req.getDescription(false).replace("uri=", ""));
        return b;
    }
}
