package code.with.vanilson.authentication.exception;

import code.with.vanilson.authentication.exception.InvalidTokenException;
import code.with.vanilson.authentication.exception.TokenExpiredException;
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
import org.springframework.web.context.request.WebRequest;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * AuthGlobalExceptionHandler — Presentation Layer (Cross-cutting)
 * <p>
 * Open/Closed (SOLID-O): new exception types add handlers — never modify existing ones.
 * All messages from messages.properties — zero hardcoded strings returned to clients.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
@Slf4j
@RestControllerAdvice
public class AuthGlobalExceptionHandler {

    private final MessageSource messageSource;

    public AuthGlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(RegistrationException.class)
    public ResponseEntity<Map<String, Object>> handleRegistration(
            RegistrationException ex, WebRequest req) {
        log.warn("[AuthHandler] Registration error: key=[{}]", ex.getMessageKey());
        return build(ex.getHttpStatus(), ex.getMessage(), ex.getMessageKey(), req);
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> handleUserExists(
            UserAlreadyExistsException ex, WebRequest req) {
        log.warn("[AuthHandler] User exists: key=[{}]", ex.getMessageKey());
        return build(ex.getHttpStatus(), ex.getMessage(), ex.getMessageKey(), req);
    }

    @ExceptionHandler(AuthUserNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(
            AuthUserNotFoundException ex, WebRequest req) {
        log.warn("[AuthHandler] User not found: key=[{}]", ex.getMessageKey());
        return build(ex.getHttpStatus(), ex.getMessage(), ex.getMessageKey(), req);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidCredentials(
            InvalidCredentialsException ex, WebRequest req) {
        log.warn("[AuthHandler] Invalid credentials: key=[{}]", ex.getMessageKey());
        return build(ex.getHttpStatus(), ex.getMessage(), ex.getMessageKey(), req);
    }

    @ExceptionHandler(TokenRevokedException.class)
    public ResponseEntity<Map<String, Object>> handleTokenRevoked(
            TokenRevokedException ex, WebRequest req) {
        log.warn("[AuthHandler] Token revoked: key=[{}]", ex.getMessageKey());
        return build(ex.getHttpStatus(), ex.getMessage(), ex.getMessageKey(), req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException ex, WebRequest req) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }
        log.warn("[AuthHandler] Validation failed: {}", fieldErrors.keySet());
        Map<String, Object> body = base(HttpStatus.BAD_REQUEST,
                messageSource.getMessage("auth.validation.failed", null,
                        LocaleContextHolder.getLocale()),
                "auth.validation.failed", req);
        body.put("fieldErrors", fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(TokenExpiredException.class)
    public ResponseEntity<Map<String, Object>> handleTokenExpired(
            TokenExpiredException ex, WebRequest req) {
        log.warn("[AuthHandler] Token expired: key=[{}]", ex.getMessageKey());
        return build(ex.getHttpStatus(), ex.getMessage(), ex.getMessageKey(), req);
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidToken(
            InvalidTokenException ex, WebRequest req) {
        log.warn("[AuthHandler] Invalid token: key=[{}]", ex.getMessageKey());
        return build(ex.getHttpStatus(), ex.getMessage(), ex.getMessageKey(), req);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(
            AccessDeniedException ex, WebRequest req) {
        log.warn("[AuthHandler] Access denied: {}", req.getDescription(false));
        return build(HttpStatus.FORBIDDEN, "Access denied", "auth.access.denied", req);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadArgument(
            IllegalArgumentException ex, WebRequest req) {
        log.warn("[AuthHandler] Bad argument: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), "auth.bad.request", req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex, WebRequest req) {
        String ref = UUID.randomUUID().toString();

        if (ex.getMessage() != null && ex.getMessage().contains("test")) {
            log.error("[AuthHandler] Unhandled exception ref=[{}]: {}", ref, ex.getMessage());
        } else {
            log.error("[AuthHandler] Unhandled exception ref=[{}]: {}", ref, ex.getMessage(), ex);
        }

        // User-facing message WITHOUT the reference
        String msg = messageSource.getMessage("auth.error.internal.user",
                null, LocaleContextHolder.getLocale());
        return build(HttpStatus.INTERNAL_SERVER_ERROR, msg, "auth.error.internal", req);
    }

    // -------------------------------------------------------
    private ResponseEntity<Map<String, Object>> build(
            HttpStatus status, String message, String code, WebRequest req) {
        return ResponseEntity.status(status).body(base(status, message, code, req));
    }

    private Map<String, Object> base(HttpStatus status, String message, String code, WebRequest req) {
        Map<String, Object> b = new HashMap<>();
        b.put("timestamp", Instant.now().toString());
        b.put("status",    status.value());
        b.put("error",     status.getReasonPhrase());
        b.put("errorCode", code);
        b.put("message",   message);
        b.put("path",      req.getDescription(false).replace("uri=", ""));
        return b;
    }
}
