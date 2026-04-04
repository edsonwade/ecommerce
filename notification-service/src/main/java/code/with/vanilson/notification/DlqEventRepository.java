package code.with.vanilson.notification;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface DlqEventRepository extends MongoRepository<DlqEvent, String> {
}
