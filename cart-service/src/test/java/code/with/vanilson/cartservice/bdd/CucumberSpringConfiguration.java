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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.MessageSource;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration",
        "application.security.jwt.secret-key=dGVzdFNlY3JldEtleUZvclRlc3RpbmdPbmx5Tm90Rm9yUHJvZHVjdGlvblVzYWdlMDAwMDAwMDAwMDAwMDAwMA=="
})
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