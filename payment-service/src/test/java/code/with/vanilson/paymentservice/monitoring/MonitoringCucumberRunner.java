package code.with.vanilson.paymentservice.monitoring;

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
 * (Testcontainers PostgreSQL + EmbeddedKafka). Kept in its own glue package
 * and feature directory because the main payment BDD suite is plain-Mockito
 * (no Spring context) and would report these steps as undefined.
 * <p>
 * The Spring object factory and feature directory are set explicitly because
 * this module now has cucumber-spring on the classpath; the main POJO suite is
 * pinned to DefaultObjectFactory in junit-platform.properties.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features-monitoring")
@ConfigurationParameter(key = FEATURES_PROPERTY_NAME, value = "classpath:features-monitoring")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "@monitoring")
@ConfigurationParameter(key = OBJECT_FACTORY_PROPERTY_NAME,
        value = "io.cucumber.spring.SpringFactory")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME,
        value = "code.with.vanilson.paymentservice.monitoring")
public class MonitoringCucumberRunner {
}
