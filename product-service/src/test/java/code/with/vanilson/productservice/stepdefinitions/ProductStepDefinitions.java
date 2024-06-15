package code.with.vanilson.productservice.stepdefinitions;

import code.with.vanilson.productservice.Product;
import code.with.vanilson.productservice.ProductRepository;
import code.with.vanilson.productservice.ProductRequest;
import io.cucumber.java.en.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;


import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ProductStepDefinitions
 *
 * @version 1.0
 * @since 2024-06-14
 */
@SpringBootTest
@AutoConfigureMockMvc
public class ProductStepDefinitions {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Given("the following products exist:")
    public void the_following_products_exist(List<Product> products) {
        productRepository.saveAll(products);
    }

    @When("I send a GET request to {string}")
    public void i_send_a_get_request_to(String endpoint) throws Exception {
        mockMvc.perform(get(endpoint)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Then("the response status should be {int}")
    public void the_response_status_should_be(int status) throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is(status));
    }

    @Then("the response should contain the following products:")
    public void the_response_should_contain_the_following_products(List<ProductRequest> products) throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(products.size())))
                .andExpect(jsonPath("$[0].id", is(products.get(0).id())))
                .andExpect(jsonPath("$[0].name", is(products.get(0).name())));
    }

    @Given("the following product exists:")
    public void the_following_product_exists(Product product) {
        productRepository.save(product);
    }

    @Then("the response should contain the product:")
    public void the_response_should_contain_the_product(ProductRequest product) throws Exception {
        mockMvc.perform(get("/api/v1/products/" + product.id())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id", is(product.id())))
                .andExpect(jsonPath("$.name", is(product.name())));
    }

    @Given("the product with ID {int} does not exist")
    public void the_product_with_id_does_not_exist(int productId) {
        productRepository.deleteById(productId);
    }

    @When("I send a POST request to {string} with the request body:")
    public void i_send_a_post_request_to_with_the_request_body(String endpoint, String body) throws Exception {
        mockMvc.perform(post(endpoint)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Then("the response should contain the product with details:")
    public void the_response_should_contain_the_product_with_details(String body) throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(jsonPath("$.name", is("Product 1")))
                .andExpect(jsonPath("$.description", is("Description 1")));
    }

    @When("I send a PUT request to {string} with the request body:")
    public void i_send_a_put_request_to_with_the_request_body(String endpoint, String body) throws Exception {
        mockMvc.perform(put(endpoint)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @When("I send a DELETE request to {string}")
    public void i_send_a_delete_request_to(String endpoint) throws Exception {
        mockMvc.perform(delete(endpoint)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());
    }
}
