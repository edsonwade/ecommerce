package code.with.vanilson.customerservice.unit;

import code.with.vanilson.customerservice.Customer;
import code.with.vanilson.customerservice.CustomerRepository;
import code.with.vanilson.customerservice.kafka.UserRegisteredConsumer;
import code.with.vanilson.customerservice.kafka.UserRegisteredEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UserRegisteredConsumerTest — unit tests for idempotent customer profile creation.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserRegisteredConsumer Unit Tests")
class UserRegisteredConsumerTest {

    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private Acknowledgment ack;

    private UserRegisteredConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new UserRegisteredConsumer(customerRepository);
    }

    private UserRegisteredEvent buildEvent(String userId) {
        return new UserRegisteredEvent(
                "evt-001", userId, "John", "Doe",
                "john@example.com", "default", Instant.now(), 1);
    }

    @Nested
    @DisplayName("onUserRegistered()")
    class OnUserRegistered {

        @Test
        @DisplayName("should create customer profile when userId does not exist")
        void shouldCreateCustomerWhenNotExists() {
            when(customerRepository.existsById("user-100")).thenReturn(false);

            consumer.onUserRegistered(buildEvent("user-100"), ack);

            ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
            verify(customerRepository).save(captor.capture());

            Customer saved = captor.getValue();
            assertThat(saved.getCustomerId()).isEqualTo("user-100");
            assertThat(saved.getFirstname()).isEqualTo("John");
            assertThat(saved.getLastname()).isEqualTo("Doe");
            assertThat(saved.getEmail()).isEqualTo("john@example.com");

            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("should skip creation and acknowledge when customer already exists (idempotent)")
        void shouldSkipWhenCustomerAlreadyExists() {
            when(customerRepository.existsById("user-100")).thenReturn(true);

            consumer.onUserRegistered(buildEvent("user-100"), ack);

            verify(customerRepository, never()).save(any());
            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("should always acknowledge regardless of whether customer was created or skipped")
        void shouldAlwaysAcknowledge() {
            when(customerRepository.existsById("user-200")).thenReturn(false);

            consumer.onUserRegistered(buildEvent("user-200"), ack);

            verify(ack).acknowledge();
        }
    }
}
