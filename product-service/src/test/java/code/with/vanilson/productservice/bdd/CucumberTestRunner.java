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
// Feature files are selected individually: selecting the "features" directory
// would also pull in the legacy products.feature from src/MAIN/resources/features
// (same classpath root name), whose glue (stepdefinitions package) is not on this
// suite's glue path and whose endpoints are stale (POST /api/v1/products vs the
// real /api/v1/products/create) — those endpoints are covered by
// ProductControllerTest. New feature files must be registered here.
@SelectClasspathResource("features/purchase_products.feature")
@SelectClasspathResource("features/search_products.feature")
@SelectClasspathResource("features/tenant_isolation.feature")
@SelectClasspathResource("features/inventory_reservation_idempotency.feature")
@SelectClasspathResource("features/seller_approval_guard.feature")
@SelectClasspathResource("features/product_status.feature")
@SelectClasspathResource("features/category_management.feature")
@SelectClasspathResource("features/refund_restock.feature")
@SelectClasspathResource("features/product_reviews.feature")
@SelectClasspathResource("features/catalog_rating.feature")
@ConfigurationParameter(key = Constants.GLUE_PROPERTY_NAME,
        value = "code.with.vanilson.productservice.bdd")
@ConfigurationParameter(key = Constants.PLUGIN_PROPERTY_NAME,
        value = "pretty, html:target/cucumber-reports/cucumber.html, json:target/cucumber-reports/cucumber.json")
@ConfigurationParameter(key = Constants.FILTER_TAGS_PROPERTY_NAME, value = "not @Ignore")
// Steps here are POJO + Mockito. cucumber-spring is on the classpath (for the
// monitoring suite), so without this pin Cucumber auto-selects SpringFactory
// and fails: no @CucumberContextConfiguration exists in this glue package.
@ConfigurationParameter(key = Constants.OBJECT_FACTORY_PROPERTY_NAME,
        value = "io.cucumber.core.backend.DefaultObjectFactory")
public class CucumberTestRunner {
}
