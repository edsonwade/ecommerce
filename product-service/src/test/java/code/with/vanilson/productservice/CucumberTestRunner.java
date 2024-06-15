package code.with.vanilson.productservice;

/**
 * CucumberTestRunner
 *
 * @author vamuhong
 * @version 1.0
 * @since 2024-06-14
 */

import io.cucumber.junit.Cucumber;
import io.cucumber.junit.CucumberOptions;
import org.junit.runner.RunWith;

import io.cucumber.junit.Cucumber;
import io.cucumber.junit.CucumberOptions;
import org.junit.runner.RunWith;

@RunWith(Cucumber.class)
@CucumberOptions(
        features = "C:\\Users\\vamuhong\\Documents\\Formation 2023\\e-commerce-microservice\\backend\\product-service\\src\\main\\resources\\features",
        glue = "code.with.vanilson.productservice.stepdefinitions"
)
public class CucumberTestRunner {
}