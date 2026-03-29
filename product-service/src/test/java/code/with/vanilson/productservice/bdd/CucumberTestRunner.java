package code.with.vanilson.productservice.bdd;

import io.cucumber.junit.platform.engine.Constants;
import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

/**
 * CucumberTestRunner — BDD Test Suite Entry Point
 * <p>
 * Runs all Cucumber features found under classpath:features/.
 * Uses junit-platform-engine (Cucumber 7+) — replaces the old @RunWith(Cucumber.class).
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = Constants.GLUE_PROPERTY_NAME,
        value = "code.with.vanilson.productservice.bdd")
@ConfigurationParameter(key = Constants.PLUGIN_PROPERTY_NAME,
        value = "pretty, html:target/cucumber-reports/cucumber.html, json:target/cucumber-reports/cucumber.json")
@ConfigurationParameter(key = Constants.FILTER_TAGS_PROPERTY_NAME, value = "not @Ignore")
public class CucumberTestRunner {
}
