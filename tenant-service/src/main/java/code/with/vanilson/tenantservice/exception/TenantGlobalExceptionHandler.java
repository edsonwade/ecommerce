package code.with.vanilson.tenantservice.exception;

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
 * TenantGlobalExceptionHandler — Presentation Layer (cross-cutting)
 * <p>
 * Handles all exceptions from TenantController.
 * Open/Closed (SOLID-O): new exception types → new handler method only.
 * All messages from messages.properties — zero hardcoded strings returned to clients.
 *
 * @author vamuhong
 * @version 4.0
 */
@Slf4j
@RestControllerAdvice
public class TenantGlobalExceptionHandler {

    private final MessageSource messageSource;

    public TenantGlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(TenantNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(TenantNotFoundException ex, WebRequest req) {
        log.warn("[TenantHandler] Not found: key=[{}]", ex.getMessageKey());
        return build(ex.getHttpStatus(), ex.getMessage(), ex.getMessageKey(), req);
    }

    @ExceptionHandler(TenantAlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> handleAlreadyExists(TenantAlreadyExistsException ex, WebRequest req) {
        log.warn("[TenantHandler] Already exists: key=[{}]", ex.getMessageKey());
        return build(ex.getHttpStatus(), ex.getMessage(), ex.getMessageKey(), req);
    }

    @ExceptionHandler(TenantNotOperationalException.class)
    public ResponseEntity<Map<String, Object>> handleNotOperational(TenantNotOperationalException ex, WebRequest req) {
        log.warn("[TenantHandler] Not operational: key=[{}]", ex.getMessageKey());
        return build(ex.getHttpStatus(), ex.getMessage(), ex.getMessageKey(), req);
    }

    @ExceptionHandler(TenantBaseException.class)
    public ResponseEntity<Map<String, Object>> handleBase(TenantBaseException ex, WebRequest req) {
        log.error("[TenantHandler] Base exception: key=[{}]", ex.getMessageKey(), ex);
        return build(ex.getHttpStatus(), ex.getMessage(), ex.getMessageKey(), req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex, WebRequest req) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }
        log.warn("[TenantHandler] Validation failed: {}", fieldErrors.keySet());
        Map<String, Object> body = base(HttpStatus.BAD_REQUEST,
                messageSource.getMessage("tenant.validation.failed", null, LocaleContextHolder.getLocale()),
                "tenant.validation.failed", req);
        body.put("fieldErrors", fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex, WebRequest req) {
        String ref = UUID.randomUUID().toString();
        String msg = messageSource.getMessage("tenant.error.internal",
                new Object[]{ref}, LocaleContextHolder.getLocale());
        log.error("[TenantHandler] Unhandled ref=[{}]: {}", ref, ex.getMessage(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, msg, "tenant.error.internal", req);
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus s, String m, String code, WebRequest req) {
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
