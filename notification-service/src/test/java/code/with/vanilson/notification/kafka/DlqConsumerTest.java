package code.with.vanilson.notification.kafka;

import code.with.vanilson.notification.DlqEvent;
import code.with.vanilson.notification.DlqEventRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DlqConsumerTest {

    @Mock DlqEventRepository dlqEventRepository;
    @InjectMocks DlqConsumer dlqConsumer;

    @Test
    void paymentDlqEvent_isSavedToMongoDB_withTopicAndPayload() {
        ConsumerRecord<String, Object> record = new ConsumerRecord<>(
                "payment-topic.DLQ", 0, 5L, "key", "{\"orderReference\":\"ORD-999\"}");

        dlqConsumer.consumePaymentDlq(record);

        ArgumentCaptor<DlqEvent> captor = ArgumentCaptor.forClass(DlqEvent.class);
        verify(dlqEventRepository).save(captor.capture());

        DlqEvent saved = captor.getValue();
        assertThat(saved.getTopic()).isEqualTo("payment-topic.DLQ");
        assertThat(saved.getOffset()).isEqualTo(5L);
        assertThat(saved.getPartition()).isEqualTo(0);
        assertThat(saved.getPayload()).contains("ORD-999");
    }

    @Test
    void orderDlqEvent_isSavedToMongoDB_withTopicAndPayload() {
        ConsumerRecord<String, Object> record = new ConsumerRecord<>(
                "order-topic.DLQ", 1, 10L, "key", "{\"orderReference\":\"ORD-777\"}");

        dlqConsumer.consumeOrderDlq(record);

        ArgumentCaptor<DlqEvent> captor = ArgumentCaptor.forClass(DlqEvent.class);
        verify(dlqEventRepository).save(captor.capture());

        DlqEvent saved = captor.getValue();
        assertThat(saved.getTopic()).isEqualTo("order-topic.DLQ");
        assertThat(saved.getOffset()).isEqualTo(10L);
        assertThat(saved.getPartition()).isEqualTo(1);
        assertThat(saved.getPayload()).contains("ORD-777");
    }
}
