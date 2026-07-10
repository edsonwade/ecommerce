package code.with.vanilson.paymentservice.bdd;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.OBJECT_FACTORY_PROPERTY_NAME;

@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME,
        value = "code.with.vanilson.paymentservice.bdd")
// POJO + Mockito glue; pin the default factory so the cucumber-spring
// SpringFactory (present for the monitoring suite) is not auto-selected.
@ConfigurationParameter(key = OBJECT_FACTORY_PROPERTY_NAME,
        value = "io.cucumber.core.backend.DefaultObjectFactory")
public class CucumberTestRunner {
}
