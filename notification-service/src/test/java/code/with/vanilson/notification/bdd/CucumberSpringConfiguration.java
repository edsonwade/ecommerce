package code.with.vanilson.notification.bdd;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

/**
 * Satisfies cucumber-spring's requirement for exactly one @CucumberContextConfiguration
 * class in the glue path. All step definitions use pure Mockito — no Spring beans
 * are injected — so an empty @Configuration context is sufficient.
 */
@CucumberContextConfiguration
@ContextConfiguration(classes = CucumberSpringConfiguration.EmptyConfig.class)
public class CucumberSpringConfiguration {

    @Configuration
    static class EmptyConfig {}
}
