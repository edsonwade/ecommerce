/**
 * Author: vanilson muhongo
 * Date:05/04/2026
 * Time:15:25
 * Version:1
 */

package code.with.vanilson.authentication.bdd;
import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

/**
 * CucumberIntegrationTest — JUnit Platform Suite runner for all Cucumber BDD scenarios.
 *
 * Discovers feature files from classpath:/features/
 * Step definitions from code.with.vanilson.authentication.bdd package.
 * Plugins: pretty console output + HTML report.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("classpath:features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME,
        value = "code.with.vanilson.authentication.bdd")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME,
        value = "pretty, html:target/cucumber-reports/auth-bdd-report.html")
public class CucumberIntegrationTest {
}
