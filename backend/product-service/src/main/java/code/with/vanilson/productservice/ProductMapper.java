package code.with.vanilson.productservice;

import code.with.vanilson.productservice.exception.ProductBadRequestException;
import code.with.vanilson.productservice.exception.ProductNullException;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class ProductMapper {

    /**
     * Converts a Product object to a ProductRequest object.
     *
     * @param product the Product object to convert
     * @return the converted ProductRequest object
     * @throws ProductNullException if the input Product has null required fields
     */
    protected ProductRequest toProductRequest(@NotNull Product product) {
        validateProduct(product);

        return new ProductRequest(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getAvailableQuantity(),
                product.getPrice()
        );
    }

    /**
     * Converts a list of Product objects to a list of ProductRequest objects.
     *
     * @param products the list of Product objects to convert
     * @return the list of converted ProductRequest objects
     * @throws ProductNullException if any of the input Product objects has null required fields
     */
    protected List<ProductRequest> toProductRequests(@NotNull List<Product> products) {
        List<ProductRequest> productRequests = new ArrayList<>();
        for (Product product : products) {
            validateProduct(product);
            productRequests.add(new ProductRequest(
                    product.getId(),
                    product.getName(),
                    product.getDescription(),
                    product.getAvailableQuantity(),
                    product.getPrice()
            ));
        }
        return productRequests;
    }


    protected void validateProduct(@NotNull Product product) {
        if (product.getId() == null) {
            throw new ProductNullException("Product id must not be null");
        }
        if (product.getName() == null) {
            throw new ProductNullException("Product name must not be null");
        }
        if (product.getDescription() == null) {
            throw new ProductNullException("Product description must not be null");
        }
        if (product.getAvailableQuantity() <= 0) {
            throw new ProductNullException("Product available quantity must not be null");
        }
        if (product.getPrice() == null) {
            throw new ProductNullException("Product price must not be null");
        }
    }

    /**
     * Converts a list of Product objects to a list of ProductResponse objects.
     *
     * @param products the list of Product objects to convert
     * @return the list of converted ProductResponse objects
     */
    protected List<ProductResponse> toProductResponse(List<Product> products) {
        return products
                .stream()
                .map(product -> new ProductResponse(product.getId(), product.getName(), product.getDescription(),
                        product.getAvailableQuantity(), product.getPrice()))
                .toList();
    }

    protected ProductResponse fromProduct(Product product) {
        if (product == null) {
            log.error("is null{}", product);
            throw new ProductBadRequestException("Product must not be null");
        }
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getAvailableQuantity(),
                product.getPrice());
    }
}
