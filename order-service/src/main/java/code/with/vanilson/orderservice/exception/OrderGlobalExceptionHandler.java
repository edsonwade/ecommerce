package code.with.vanilson.orderservice.exception;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import code.with.vanilson.orderservice.OrderStatus;
import code.with.vanilson.tenantcontext.exception.MissingTenantException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * OrderGlobalExceptionHandler
 * <p>
 * Centralised exception handler for the Order Service.
 * Catches all OrderBaseException subtypes and maps them to structured HTTP responses.
 * All error messages are resolved from messages.properties via MessageSource.
 * Never returns raw Java exception messages to the client.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
@Slf4j
@RestControllerAdvice
public class OrderGlobalExceptionHandler {

    private final MessageSource messageSource;

    public OrderGlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(OrderForbiddenException.class)
    public ResponseEntity<Map<String, Object>> handleOrderForbidden(
            OrderForbiddenException ex, WebRequest request) {
        log.warn("[OrderExceptionHandler] Forbidden: key=[{}] message=[{}]",
                ex.getMessageKey(), ex.getMessage());
        return buildResponse(ex.getHttpStatus(), ex.getMessage(), ex.getMessageKey(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(
            AccessDeniedException ex, WebRequest request) {
        String msg = messageSource.getMessage("order.access.denied", null, LocaleContextHolder.getLocale());
        log.warn("[OrderExceptionHandler] Access denied: {}", ex.getMessage());
        return buildResponse(HttpStatus.FORBIDDEN, msg, "order.access.denied", request);
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleOrderNotFound(
            OrderNotFoundException ex, WebRequest request) {
        log.warn("[OrderExceptionHandler] Order not found: key=[{}] message=[{}]",
                ex.getMessageKey(), ex.getMessage());
        return buildResponse(ex.getHttpStatus(), ex.getMessage(), ex.getMessageKey(), request);
    }

    @ExceptionHandler(OrderValidationException.class)
    public ResponseEntity<Map<String, Object>> handleOrderValidation(
            OrderValidationException ex, WebRequest request) {
        log.warn("[OrderExceptionHandler] Validation error: key=[{}] message=[{}]",
                ex.getMessageKey(), ex.getMessage());
        return buildResponse(ex.getHttpStatus(), ex.getMessage(), ex.getMessageKey(), request);
    }

    @ExceptionHandler(OrderDuplicateReferenceException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateReference(
            OrderDuplicateReferenceException ex, WebRequest request) {
        log.warn("[OrderExceptionHandler] Duplicate reference: key=[{}] message=[{}]",
                ex.getMessageKey(), ex.getMessage());
        return buildResponse(ex.getHttpStatus(), ex.getMessage(), ex.getMessageKey(), request);
    }

    @ExceptionHandler(CustomerNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleCustomerNotFound(
            CustomerNotFoundException ex, WebRequest request) {
        log.warn("[OrderExceptionHandler] Customer not found: key=[{}] message=[{}]",
                ex.getMessageKey(), ex.getMessage());
        return buildResponse(ex.getHttpStatus(), ex.getMessage(), ex.getMessageKey(), request);
    }

    @ExceptionHandler(CustomerServiceUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleCustomerServiceUnavailable(
            CustomerServiceUnavailableException ex, WebRequest request) {
        log.error("[OrderExceptionHandler] Customer service unavailable: key=[{}] cause=[{}]",
                ex.getMessageKey(), ex.getCause() != null ? ex.getCause().getMessage() : "none");
        return buildResponse(ex.getHttpStatus(), ex.getMessage(), ex.getMessageKey(), request);
    }


    @ExceptionHandler(OrderInternalServiceException.class)
    public ResponseEntity<Map<String, Object>> handleOrderInternalService(
            OrderInternalServiceException ex, WebRequest request) {
        log.warn("[OrderExceptionHandler] Internal service error (expected/test): key=[{}] message=[{}]",
                ex.getMessageKey(), ex.getMessage());
        return buildResponse(ex.getHttpStatus(), ex.getMessage(), ex.getMessageKey(), request);
    }

    /**
     * Handles Bean Validation errors (@Valid on request bodies).
     * Returns field-level error details without exposing internal class names.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(
            MethodArgumentNotValidException ex, WebRequest request) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }
        log.warn("[OrderExceptionHandler] Bean validation failed: fields={}", fieldErrors.keySet());

        String message = messageSource.getMessage(
                "order.validation.failed",
                null,
                LocaleContextHolder.getLocale());

        Map<String, Object> body = buildBaseBody(HttpStatus.BAD_REQUEST,
                message, "order.validation.failed", request);
        body.put("fieldErrors", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * Handles NoResourceFoundException specifically (framework 404s).
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResourceFound(
            NoResourceFoundException ex, WebRequest request) {
        log.debug("[OrderExceptionHandler] Resource not found: {}", ex.getMessage());

        String message = messageSource.getMessage(
                "error.resource.not.found",
                null,
                LocaleContextHolder.getLocale());
        return buildResponse(HttpStatus.NOT_FOUND, message, "error.resource.not.found", request);
    }

    @ExceptionHandler(MissingTenantException.class)
    public ResponseEntity<Map<String, Object>> handleMissingTenant(
            MissingTenantException ex, WebRequest request) {
        log.warn("[OrderExceptionHandler] Missing tenant: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), "order.tenant.missing", request);
    }

    /**
     * Fallback for any {@link OrderBaseException} subtype that lacks a dedicated handler
     * (e.g. {@link OrderIllegalStateTransitionException} → 409). Maps the exception's own
     * {@code httpStatus} + message key — without this the subtype would fall through to the
     * generic {@code Exception} handler and be masked as a 500. Specific handlers above still
     * win by type specificity, so their behaviour is unchanged.
     */
    @ExceptionHandler(OrderBaseException.class)
    public ResponseEntity<Map<String, Object>> handleOrderBase(
            OrderBaseException ex, WebRequest request) {
        log.warn("[OrderExceptionHandler] Order error: key=[{}] status=[{}] message=[{}]",
                ex.getMessageKey(), ex.getHttpStatus().value(), ex.getMessage());
        return buildResponse(ex.getHttpStatus(), ex.getMessage(), ex.getMessageKey(), request);
    }

    /**
     * Handles an unreadable/malformed JSON body. The common case on the fulfillment endpoint is
     * an unknown {@link OrderStatus} name (e.g. {@code {"status":"PAUSED"}}), which Jackson raises
     * as {@link InvalidFormatException} — mapped to {@code 400 order.status.invalid}. Any other
     * unreadable body → generic {@code 400 order.request.unreadable}. Without this the request
     * would fall to the 500 catch-all.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadableBody(
            HttpMessageNotReadableException ex, WebRequest request) {
        if (ex.getCause() instanceof InvalidFormatException ife
                && ife.getTargetType() != null
                && ife.getTargetType().isAssignableFrom(OrderStatus.class)) {
            String message = messageSource.getMessage(
                    "order.status.invalid", null, LocaleContextHolder.getLocale());
            log.warn("[OrderExceptionHandler] Invalid order status value in request body");
            return buildResponse(HttpStatus.BAD_REQUEST, message, "order.status.invalid", request);
        }
        String message = messageSource.getMessage(
                "order.request.unreadable", null, LocaleContextHolder.getLocale());
        log.warn("[OrderExceptionHandler] Unreadable request body: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, message, "order.request.unreadable", request);
    }

    /**
     * Handles NullPointerException specifically.
     * NPEs are always bugs and should be logged with priority but clean format.
     */
    @ExceptionHandler(NullPointerException.class)
    public ResponseEntity<Map<String, Object>> handleNullPointerException(
            NullPointerException ex, WebRequest request) {
        String ref = UUID.randomUUID().toString();

        log.error("[OrderExceptionHandler] BUG DETECTED - NullPointerException ref=[{}]: {}",
                ref, ex.getMessage());
        log.debug("[OrderExceptionHandler] BUG DETECTED - NullPointerException ref=[{}] stacktrace:", ref, ex);

        String message = messageSource.getMessage(
                "order.error.internal.user",
                null,
                LocaleContextHolder.getLocale());
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, message, "order.error.bug.npe", request);
    }

    /**
     * Fallback for any unexpected exception — never exposes stack traces to the client.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(
            Exception ex, WebRequest request) {
        String ref = UUID.randomUUID().toString();

        log.error("[OrderExceptionHandler] Unhandled exception ref=[{}]: {}", ref, ex.getMessage());
        log.debug("[OrderExceptionHandler] Unhandled exception ref=[{}] stacktrace:", ref, ex);

        // User-facing message WITHOUT the reference
        String message = messageSource.getMessage(
                "order.error.internal.user",
                null,
                LocaleContextHolder.getLocale());
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, message, "order.error.internal", request);
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
