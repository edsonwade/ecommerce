package code.with.vanilson.orderservice.bdd;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.OBJECT_FACTORY_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

/**
 * CucumberSagaIntegrationTest — Cucumber suite runner for saga BDD scenarios (Phase 0)
 * <p>
 * Runs only scenarios tagged with @saga from order_saga.feature.
 * Uses the same glue path as CucumberTestRunner so it picks up
 * OrderSagaStepDefinitions automatically.
 * </p>
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "@saga")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME,
        value = "code.with.vanilson.orderservice.bdd")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME,
        value = "pretty, html:target/cucumber-reports/order-saga-bdd-report.html")
// POJO + Mockito glue; pin the default factory so the cucumber-spring
// SpringFactory (present for the monitoring suite) is not auto-selected.
@ConfigurationParameter(key = OBJECT_FACTORY_PROPERTY_NAME,
        value = "io.cucumber.core.backend.DefaultObjectFactory")
public class CucumberSagaIntegrationTest {
}
