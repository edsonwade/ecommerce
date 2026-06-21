package code.with.vanilson.orderservice.customer;

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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CustomerEventConsumerTest — unit tests for CustomerSnapshot upsert logic.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CustomerEventConsumer Unit Tests")
class CustomerEventConsumerTest {

    @Mock
    private CustomerSnapshotRepository snapshotRepository;
    @Mock
    private Acknowledgment ack;

    private CustomerEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new CustomerEventConsumer(snapshotRepository);
    }

    private CustomerProfileEvent buildEvent(String customerId, String eventType) {
        return new CustomerProfileEvent(
                "evt-001", customerId, "Maria", "Santos",
                "maria@example.com",
                "Rua das Flores", "42", "1000-001", "Lisboa", "Portugal",
                eventType, Instant.now(), 2);
    }

    @Nested
    @DisplayName("onCustomerProfile()")
    class OnCustomerProfile {

        @Test
        @DisplayName("should create new snapshot when customer does not exist")
        void shouldCreateSnapshotWhenNotExists() {
            when(snapshotRepository.findById("cust-001")).thenReturn(Optional.empty());
            when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            consumer.onCustomerProfile(buildEvent("cust-001", "CREATED"), ack);

            ArgumentCaptor<CustomerSnapshot> captor = ArgumentCaptor.forClass(CustomerSnapshot.class);
            verify(snapshotRepository).save(captor.capture());

            CustomerSnapshot snapshot = captor.getValue();
            assertThat(snapshot.getCustomerId()).isEqualTo("cust-001");
            assertThat(snapshot.getFirstname()).isEqualTo("Maria");
            assertThat(snapshot.getLastname()).isEqualTo("Santos");
            assertThat(snapshot.getEmail()).isEqualTo("maria@example.com");
            assertThat(snapshot.getStreet()).isEqualTo("Rua das Flores");
            assertThat(snapshot.getCity()).isEqualTo("Lisboa");
            assertThat(snapshot.getCountry()).isEqualTo("Portugal");
            assertThat(snapshot.getLastUpdated()).isNotNull();

            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("should update existing snapshot when UPDATED event arrives")
        void shouldUpdateSnapshotWhenExists() {
            CustomerSnapshot existing = CustomerSnapshot.builder()
                    .customerId("cust-001")
                    .firstname("Maria")
                    .lastname("Santos")
                    .email("old@example.com")
                    .build();

            when(snapshotRepository.findById("cust-001")).thenReturn(Optional.of(existing));
            when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CustomerProfileEvent updateEvent = new CustomerProfileEvent(
                    "evt-002", "cust-001", "Maria", "Santos",
                    "new@example.com",
                    "Rua Nova", "7", "4000-002", "Porto", "Portugal",
                    "UPDATED", Instant.now(), 2);

            consumer.onCustomerProfile(updateEvent, ack);

            ArgumentCaptor<CustomerSnapshot> captor = ArgumentCaptor.forClass(CustomerSnapshot.class);
            verify(snapshotRepository).save(captor.capture());

            assertThat(captor.getValue().getEmail()).isEqualTo("new@example.com");
            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("should always acknowledge after saving snapshot")
        void shouldAlwaysAcknowledge() {
            when(snapshotRepository.findById("cust-002")).thenReturn(Optional.empty());
            when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            consumer.onCustomerProfile(buildEvent("cust-002", "CREATED"), ack);

            verify(ack).acknowledge();
        }
    }
}
