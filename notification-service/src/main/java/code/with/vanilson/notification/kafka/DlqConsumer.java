package code.with.vanilson.notification.kafka;

import code.with.vanilson.notification.DlqEvent;
import code.with.vanilson.notification.DlqEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class DlqConsumer {

    private final DlqEventRepository dlqEventRepository;

    @KafkaListener(
            topics = "payment-topic.DLQ",
            groupId = "paymentDlqGroup",
            containerFactory = "kafkaListenerContainerFactory")
    public void consumePaymentDlq(ConsumerRecord<String, Object> record) {
        dqlEventLog(record);
        dlqEventRepository.save(
                DlqEvent.of(record.topic(), record.partition(), record.offset(), record.value()));
    }

    @KafkaListener(
            topics = "order-topic.DLQ",
            groupId = "orderDlqGroup",
            containerFactory = "kafkaListenerContainerFactory")
    public void consumeOrderDlq(ConsumerRecord<String, Object> record) {
        dqlEventLog(record);
        dlqEventRepository.save(
                DlqEvent.of(record.topic(), record.partition(), record.offset(), record.value()));
    }

    private static void dqlEventLog(ConsumerRecord<String, Object> record) {
        log.error("DLQ event received — topic={}, partition={}, offset={}, payload={}",
                record.topic(), record.partition(), record.offset(), record.value());
    }
}
