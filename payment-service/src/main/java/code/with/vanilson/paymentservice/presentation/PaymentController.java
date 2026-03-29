package code.with.vanilson.paymentservice.presentation;

import code.with.vanilson.paymentservice.application.PaymentRequest;
import code.with.vanilson.paymentservice.application.PaymentResponse;
import code.with.vanilson.paymentservice.application.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * PaymentController — Presentation Layer
 * <p>
 * REST controller for the Payment Service.
 * Single Responsibility (SOLID-S): only handles HTTP concerns.
 * All business logic is delegated to PaymentService.
 * <p>
 * Endpoints:
 * POST /api/v1/payments        → creates a payment (idempotent)
 * GET  /api/v1/payments        → lists all payments
 * GET  /api/v1/payments/{id}   → gets a single payment by ID
 * <p>
 * CHANGED FROM ORIGINAL:
 * - POST returns 201 Created (was 200 OK — semantically incorrect)
 * - Added GET endpoints for observability
 * - Path changed from /create-payment to / (REST convention)
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * Creates a new payment. Idempotent — retrying with the same orderReference
     * returns the original payment ID without processing a second charge.
     */
    @PostMapping
    public ResponseEntity<Integer> createPayment(@RequestBody @Valid PaymentRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(paymentService.createPayment(request));
    }

    /**
     * Returns all payments.
     */
    @GetMapping
    public ResponseEntity<List<PaymentResponse>> findAll() {
        return ResponseEntity.ok(paymentService.findAllPayments());
    }

    /**
     * Returns a payment by ID.
     * Returns 404 if not found (PaymentNotFoundException handled by PaymentGlobalExceptionHandler).
     */
    @GetMapping("/{payment-id}")
    public ResponseEntity<PaymentResponse> findById(@PathVariable("payment-id") Integer paymentId) {
        return ResponseEntity.ok(paymentService.findById(paymentId));
    }
}
