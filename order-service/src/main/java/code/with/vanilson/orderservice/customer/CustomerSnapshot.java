package code.with.vanilson.orderservice.customer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * CustomerSnapshot — CQRS read model for customer data in order-service.
 * <p>
 * Populated by {@link CustomerEventConsumer} which consumes {@code customer.profile} events
 * published by customer-service on every create/update.
 * <p>
 * Purpose: avoid a synchronous HTTP call to customer-service on every order creation.
 * OrderService checks this table first; if present it uses the snapshot data directly
 * (no network call). Falls back to Feign only for brand-new customers whose
 * snapshot has not yet arrived (eventual consistency window).
 * </p>
 *
 * @author vamuhong
 * @version 1.0
 */
@Entity
@Table(name = "customer_snapshot")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class CustomerSnapshot {

    @Id
    @Column(name = "customer_id", nullable = false, length = 64)
    private String customerId;

    @Column(name = "firstname", length = 128)
    private String firstname;

    @Column(name = "lastname", length = 128)
    private String lastname;

    @Column(name = "email", length = 256)
    private String email;

    @Column(name = "tenant_id", length = 64)
    private String tenantId;

    /** Shipping address — carried from customer-service via the customer.profile event. */
    @Column(name = "street", length = 256)
    private String street;

    @Column(name = "house_number", length = 64)
    private String houseNumber;

    @Column(name = "zip_code", length = 64)
    private String zipCode;

    @Column(name = "city", length = 128)
    private String city;

    @Column(name = "country", length = 128)
    private String country;

    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;
}
