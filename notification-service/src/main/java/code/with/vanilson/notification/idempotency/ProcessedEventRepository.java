package code.with.vanilson.notification.idempotency;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface ProcessedEventRepository extends MongoRepository<ProcessedEvent, String> {
}
