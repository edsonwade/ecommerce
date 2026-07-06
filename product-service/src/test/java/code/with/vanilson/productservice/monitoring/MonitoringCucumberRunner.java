package code.with.vanilson.productservice.monitoring;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.FEATURES_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.OBJECT_FACTORY_PROPERTY_NAME;

/**
 * Runs the @monitoring BDD scenarios against a full Spring context
 * (Testcontainers PostgreSQL + Redis + EmbeddedKafka). Kept in its own glue
 * package and feature directory so the main product BDD suite (classpath
 * features/ + bdd glue) never sees these Spring-backed steps.
 * <p>
 * Object factory, features and glue are all declared explicitly on the runner
 * — module-global cucumber.* properties would leak between suites (see the
 * order-service junit-platform.properties incident).
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features-monitoring")
@ConfigurationParameter(key = FEATURES_PROPERTY_NAME, value = "classpath:features-monitoring")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "@monitoring")
@ConfigurationParameter(key = OBJECT_FACTORY_PROPERTY_NAME,
        value = "io.cucumber.spring.SpringFactory")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME,
        value = "code.with.vanilson.productservice.monitoring")
public class MonitoringCucumberRunner {
}
