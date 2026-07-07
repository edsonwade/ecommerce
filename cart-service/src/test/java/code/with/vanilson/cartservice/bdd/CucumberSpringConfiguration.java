package code.with.vanilson.cartservice.bdd;

/**
 * CucumberSpringConfiguration
 *
 * @author vamuhong
 * @version 1.0
 * @since 2024-06-14
 */


import code.with.vanilson.cartservice.application.CartMapper;
import code.with.vanilson.cartservice.infrastructure.CartRepository;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.MessageSource;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration",
        "application.security.jwt.secret-key=dGVzdFNlY3JldEtleUZvclRlc3RpbmdPbmx5Tm90Rm9yUHJvZHVjdGlvblVzYWdlMDAwMDAwMDAwMDAwMDAwMA==",
        "management.endpoints.web.exposure.include=health,prometheus"
})
@AutoConfigureMockMvc
// Metrics exporters are disabled by default in Spring Boot tests; needed so the
// @monitoring scenarios can hit a real /actuator/prometheus endpoint.
@AutoConfigureObservability(tracing = false)
@CucumberContextConfiguration
@SuppressWarnings("unused")
public class CucumberSpringConfiguration {


    @MockBean
    private CartRepository cartRepository;

    @MockBean
    private CartMapper cartMapper;

    @MockBean
    private MessageSource messageSource;

}