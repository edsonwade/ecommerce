package code.with.vanilson.customerservice.unit;

import code.with.vanilson.customerservice.Address;
import code.with.vanilson.customerservice.Customer;
import code.with.vanilson.customerservice.kafka.CustomerProfileEvent;
import code.with.vanilson.customerservice.kafka.CustomerProfileProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CustomerProfileProducerTest — unit tests for Kafka event publishing.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CustomerProfileProducer Unit Tests")
class CustomerProfileProducerTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private CustomerProfileProducer producer;

    @BeforeEach
    void setUp() {
        producer = new CustomerProfileProducer(kafkaTemplate);
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    @Nested
    @DisplayName("publishProfileEvent()")
    class PublishProfileEvent {

        @Test
        @DisplayName("should publish to customer.profile topic with customerId as partition key")
        void shouldPublishToCorrectTopicWithKey() {
            Customer customer = Customer.builder()
                    .customerId("cust-001")
                    .firstname("Ana")
                    .lastname("Silva")
                    .email("ana@example.com")
                    .address(Address.builder()
                            .street("Rua das Flores").houseNumber("42")
                            .zipCode("1000-001").city("Lisboa").country("Portugal").build())
                    .build();

            producer.publishProfileEvent(customer, "CREATED");

            ArgumentCaptor<CustomerProfileEvent> eventCaptor =
                    ArgumentCaptor.forClass(CustomerProfileEvent.class);

            verify(kafkaTemplate).send(
                    eq(CustomerProfileProducer.TOPIC),
                    eq("cust-001"),
                    eventCaptor.capture());

            CustomerProfileEvent event = eventCaptor.getValue();
            assertThat(event.customerId()).isEqualTo("cust-001");
            assertThat(event.firstname()).isEqualTo("Ana");
            assertThat(event.lastname()).isEqualTo("Silva");
            assertThat(event.email()).isEqualTo("ana@example.com");
            assertThat(event.eventType()).isEqualTo("CREATED");
            assertThat(event.schemaVersion()).isEqualTo(2);
            assertThat(event.street()).isEqualTo("Rua das Flores");
            assertThat(event.houseNumber()).isEqualTo("42");
            assertThat(event.zipCode()).isEqualTo("1000-001");
            assertThat(event.city()).isEqualTo("Lisboa");
            assertThat(event.country()).isEqualTo("Portugal");
            assertThat(event.eventId()).isNotBlank();
            assertThat(event.occurredAt()).isNotNull();
        }

        @Test
        @DisplayName("should set eventType=UPDATED when publishing update event")
        void shouldSetUpdatedEventType() {
            Customer customer = Customer.builder()
                    .customerId("cust-002")
                    .firstname("Carlos")
                    .lastname("Ferreira")
                    .email("carlos@example.com")
                    .build();

            producer.publishProfileEvent(customer, "UPDATED");

            ArgumentCaptor<CustomerProfileEvent> eventCaptor =
                    ArgumentCaptor.forClass(CustomerProfileEvent.class);
            verify(kafkaTemplate).send(anyString(), anyString(), eventCaptor.capture());

            assertThat(eventCaptor.getValue().eventType()).isEqualTo("UPDATED");
        }
    }
}
