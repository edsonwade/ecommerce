package code.with.vanilson.customerservice.bdd;

/**
 * CucumberSpringConfiguration
 *
 * @author vamuhong
 * @version 1.0
 * @since 2024-06-14
 */

import code.with.vanilson.customerservice.CustomerMapper;
import code.with.vanilson.customerservice.CustomerRepository;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.MessageSource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;

// Same pattern as order/payment/product's MonitoringCucumberSpringConfiguration:
// customer-service declares NewTopic beans (KafkaCustomerTopicConfig), so KafkaAdmin
// tries to verify/create them against a real broker at context startup. Without
// EmbeddedKafka this blocks ~30-60s and dumps a KafkaAdmin ERROR stack trace even
// though the test itself never touches Kafka. @ActiveProfiles("test") also activates
// application-test.yml, which disables Eureka registration for this context.
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration,org.springframework.boot.actuate.autoconfigure.data.redis.RedisReactiveHealthContributorAutoConfiguration",
        "management.health.redis.enabled=false",
        "management.endpoints.web.exposure.include=health,prometheus",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
@EmbeddedKafka(
        partitions = 1,
        topics = {"customer.profile", "customer.profile.DLQ"},
        brokerProperties = {
                "auto.create.topics.enable=true",
                // Cap internal-topic index preallocation — without these the embedded
                // broker preallocates ~2.3 GB of index files per run in %TEMP%.
                "offsets.topic.num.partitions=1",
                "transaction.state.log.num.partitions=1",
                "transaction.state.log.replication.factor=1",
                "transaction.state.log.min.isr=1",
                "log.index.size.max.bytes=1048576"
        })
@ActiveProfiles("test")
@AutoConfigureMockMvc
// Metrics exporters are disabled by default in Spring Boot tests; needed so the
// @monitoring scenarios can hit a real /actuator/prometheus endpoint.
@AutoConfigureObservability(tracing = false)
@CucumberContextConfiguration
@SuppressWarnings("unused")
public class CucumberSpringConfiguration {

    @MockBean
    private MongoTemplate mongoTemplate;

    @MockBean
    private CustomerRepository customerRepository;

    @MockBean
    private CustomerMapper customerMapper;

    @MockBean
    private MessageSource messageSource;

    // No live Redis in the Cucumber context — CustomerServiceConfig.flushStaleCustomerCache()
    // needs a StringRedisTemplate at startup; mock both it and the connection factory so the
    // full Spring context can load without a broker.
    @MockBean
    private RedisConnectionFactory redisConnectionFactory;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

}