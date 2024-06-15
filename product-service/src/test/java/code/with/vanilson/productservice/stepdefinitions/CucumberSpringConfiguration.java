package code.with.vanilson.productservice.stepdefinitions;

/**
 * CucumberSpringConfiguration
 *
 * @author vamuhong
 * @version 1.0
 * @since 2024-06-14
 */
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

import code.with.vanilson.productservice.ProductServiceApplication; // Import your main application class

@CucumberContextConfiguration
@SpringBootTest(classes = ProductServiceApplication.class)
public class CucumberSpringConfiguration {
}