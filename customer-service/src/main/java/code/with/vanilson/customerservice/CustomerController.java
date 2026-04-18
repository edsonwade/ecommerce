package code.with.vanilson.customerservice;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * CustomerController — Presentation Layer
 * <p>
 * REST controller for customer profile management.
 * Single Responsibility (SOLID-S): HTTP concerns only — all business logic in CustomerService.
 * <p>
 * REST best practices applied:
 * - GET    /api/v1/customers          → list all customers
 * - GET    /api/v1/customers/{id}     → get by ID
 * - GET    /api/v1/customers/email?address=x → get by email (query param, not path)
 * - POST   /api/v1/customers          → create customer (returns 201)
 * - PUT    /api/v1/customers/{id}     → full update (returns 200)
 * - DELETE /api/v1/customers/{id}     → delete (returns 204)
 * <p>
 * BUGS FIXED from original:
 * - Removed duplicate @GetMapping on root (caused ambiguous mapping error at startup)
 * - getById now calls getCustomerById, not findByEmail
 * - Removed non-REST paths: /create-customer, /update-customer/{id}, /delete-customer/{id}
 * - DELETE returns 204 No Content (was 202 Accepted)
 * - Added Swagger OpenAPI annotations on all endpoints
 * </p>
 *
 * @author vamuhong
 * @version 3.0
 */
@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Customer API", description = "Customer profile management")
public class CustomerController {

    private final CustomerService customerService;

    // -------------------------------------------------------
    // GET — list all
    // -------------------------------------------------------

    @Operation(summary = "List all customers")
    @ApiResponse(responseCode = "200", description = "Customer list returned")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<List<CustomerResponse>> getAllCustomers() {
        log.info("GET /api/v1/customers — list all customers");
        return ResponseEntity.ok(customerService.findAllCustomers());
    }

    // -------------------------------------------------------
    // GET — by ID
    // -------------------------------------------------------

    @Operation(summary = "Get customer by ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Customer found"),
        @ApiResponse(responseCode = "404", description = "Customer not found")
    })
    @PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.userId.toString()")
    @GetMapping("/{id}")
    public ResponseEntity<CustomerResponse> getCustomerById(
            @PathVariable @Parameter(description = "Customer ID") String id) {
        log.info("GET /api/v1/customers/{} — get by ID", id);
        return ResponseEntity.ok(customerService.getCustomerById(id));
    }

    // -------------------------------------------------------
    // GET — by email (query param, not path segment — email contains @)
    // -------------------------------------------------------

    @Operation(summary = "Get customer by email",
               description = "Use query parameter: /api/v1/customers/by-email?address=user@example.com")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Customer found"),
        @ApiResponse(responseCode = "404", description = "Customer not found")
    })
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/by-email")
    public ResponseEntity<CustomerResponse> getCustomerByEmail(
            @RequestParam @Parameter(description = "Customer email address") String address) {
        log.info("GET /api/v1/customers/by-email?address={} — get by email", address);
        return ResponseEntity.ok(customerService.findByEmail(address));
    }

    // -------------------------------------------------------
    // POST — create
    // -------------------------------------------------------

    @Operation(summary = "Create a new customer")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Customer created"),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "409", description = "Email already registered")
    })
    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public ResponseEntity<String> createCustomer(@RequestBody @Valid CustomerRequest request) {
        log.info("POST /api/v1/customers — create customer email=[{}]", request.email());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(customerService.createCustomer(request));
    }

    // -------------------------------------------------------
    // PUT — full update
    // -------------------------------------------------------

    @Operation(summary = "Update customer by ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Customer updated"),
        @ApiResponse(responseCode = "404", description = "Customer not found"),
        @ApiResponse(responseCode = "400", description = "Invalid request data")
    })
    @PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.userId.toString()")
    @PutMapping("/{id}")
    public ResponseEntity<CustomerResponse> updateCustomer(
            @PathVariable String id,
            @RequestBody @Valid CustomerRequest request) {
        log.info("PUT /api/v1/customers/{} — update customer", id);
        return ResponseEntity.ok(customerService.updateCustomer(id, request));
    }

    // -------------------------------------------------------
    // DELETE
    // -------------------------------------------------------

    @Operation(summary = "Delete customer by ID")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Customer deleted"),
        @ApiResponse(responseCode = "404", description = "Customer not found")
    })
    @PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.userId.toString()")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCustomer(@PathVariable String id) {
        log.info("DELETE /api/v1/customers/{} — delete customer", id);
        customerService.deleteCustomer(id);
        return ResponseEntity.noContent().build();
    }
}
