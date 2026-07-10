package code.with.vanilson.orderservice.bdd;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.OBJECT_FACTORY_PROPERTY_NAME;

/**
 * Cucumber JUnit 5 test runner for order-service BDD tests.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME,
        value = "code.with.vanilson.orderservice.bdd")
// POJO + Mockito glue; pin the default factory so the cucumber-spring
// SpringFactory (present for the monitoring suite) is not auto-selected.
@ConfigurationParameter(key = OBJECT_FACTORY_PROPERTY_NAME,
        value = "io.cucumber.core.backend.DefaultObjectFactory")
public class CucumberTestRunner {
}
