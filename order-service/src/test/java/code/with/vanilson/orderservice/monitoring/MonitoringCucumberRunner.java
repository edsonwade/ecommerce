package code.with.vanilson.orderservice.monitoring;

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
 * and feature directory because the main order BDD suite is plain-Mockito
 * (no Spring context) and would report these steps as undefined.
 * <p>
 * cucumber.features, the tag filter and the Spring object factory are set
 * explicitly because junit-platform.properties pins module-wide defaults for
 * the main POJO suite (classpath:features + DefaultObjectFactory), and those
 * file-level properties leak into every cucumber discovery in this module.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features-monitoring")
@ConfigurationParameter(key = FEATURES_PROPERTY_NAME, value = "classpath:features-monitoring")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "@monitoring")
@ConfigurationParameter(key = OBJECT_FACTORY_PROPERTY_NAME,
        value = "io.cucumber.spring.SpringFactory")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME,
        value = "code.with.vanilson.orderservice.monitoring")
public class MonitoringCucumberRunner {
}
