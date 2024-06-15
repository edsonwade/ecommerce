package code.with.vanilson.notification.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;

@Configuration
public class MongoConfig {


    @Value("${data.mongodb.username}")
    private String username;

    @Value("${data.mongodb.password}")
    private String password;
    @Bean
    public MongoTemplate mongoTemplate() {
        String databaseUri = "mongodb+srv://" + username + ":" + password + "@dev.4ezbsq2.mongodb.net/notification_service_db";
        return new MongoTemplate(new SimpleMongoClientDatabaseFactory(databaseUri));
    }
}
