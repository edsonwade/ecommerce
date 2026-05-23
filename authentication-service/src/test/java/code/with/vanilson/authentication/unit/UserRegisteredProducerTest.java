package code.with.vanilson.authentication.unit;

import code.with.vanilson.authentication.domain.Role;
import code.with.vanilson.authentication.domain.User;
import code.with.vanilson.authentication.infrastructure.kafka.UserRegisteredEvent;
import code.with.vanilson.authentication.infrastructure.kafka.UserRegisteredProducer;
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
 * UserRegisteredProducerTest — unit tests for user registration Kafka event.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserRegisteredProducer Unit Tests")
class UserRegisteredProducerTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private UserRegisteredProducer producer;

    @BeforeEach
    void setUp() {
        producer = new UserRegisteredProducer(kafkaTemplate);
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    private User buildUser() {
        return User.builder()
                .id(42L)
                .firstname("João")
                .lastname("Costa")
                .email("joao@example.com")
                .role(Role.USER)
                .tenantId("tenant-001")
                .accountEnabled(true)
                .accountLocked(false)
                .build();
    }

    @Nested
    @DisplayName("publishUserRegistered()")
    class PublishUserRegistered {

        @Test
        @DisplayName("should publish to user.registered topic with userId as partition key")
        void shouldPublishToCorrectTopicWithKey() {
            User user = buildUser();

            producer.publishUserRegistered(user);

            ArgumentCaptor<UserRegisteredEvent> eventCaptor =
                    ArgumentCaptor.forClass(UserRegisteredEvent.class);

            verify(kafkaTemplate).send(
                    eq(UserRegisteredProducer.TOPIC),
                    eq("42"),
                    eventCaptor.capture());

            UserRegisteredEvent event = eventCaptor.getValue();
            assertThat(event.userId()).isEqualTo("42");
            assertThat(event.firstname()).isEqualTo("João");
            assertThat(event.lastname()).isEqualTo("Costa");
            assertThat(event.email()).isEqualTo("joao@example.com");
            assertThat(event.tenantId()).isEqualTo("tenant-001");
            assertThat(event.schemaVersion()).isEqualTo(1);
            assertThat(event.eventId()).isNotBlank();
            assertThat(event.occurredAt()).isNotNull();
        }
    }
}
