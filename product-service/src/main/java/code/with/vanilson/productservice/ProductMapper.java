package code.with.vanilson.productservice;

import code.with.vanilson.productservice.category.Category;
import code.with.vanilson.productservice.exception.ProductBadRequestException;
import code.with.vanilson.productservice.exception.ProductNullException;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class ProductMapper {

    private final MessageSource messageSource;

    public ProductMapper(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

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
                product.getPrice(),
                product.getCategory().getId()
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
                    product.getPrice(),
                    product.getCategory().getId()
            ));
        }
        return productRequests;
    }

    /**
     * Validates that all required product fields are not null and valid.
     * Uses messages from messages.properties for internationalization.
     *
     * @param product the Product object to validate
     * @throws ProductNullException if any required field is null or invalid
     */
    protected void validateProduct(@NotNull Product product) {
        validateProductId(product.getId());
        validateProductName(product.getName());
        validateProductDescription(product.getDescription());
        validateProductQuantity(product.getAvailableQuantity());
        validateProductPrice(product.getPrice());
        validateProductCategory(product.getCategory());
    }

    private void validateProductId(Integer productId) {
        if (productId == null) {
            throw new ProductNullException(resolve("product.id.null"), "product.id.null");
        }
    }

    private void validateProductName(String name) {
        if (name == null) {
            throw new ProductNullException(resolve("product.name.null"), "product.name.null");
        }
    }

    private void validateProductDescription(String description) {
        if (description == null) {
            throw new ProductNullException(resolve("product.description.null"), "product.description.null");
        }
    }

    private void validateProductQuantity(Double quantity) {
        if (quantity == null || quantity <= 0) {
            throw new ProductNullException(resolve("product.quantity.negative"), "product.quantity.negative");
        }
    }

    private void validateProductPrice(java.math.BigDecimal price) {
        if (price == null) {
            throw new ProductNullException(resolve("product.price.null"), "product.price.null");
        }
    }

    private void validateProductCategory(Category category) {
        if (category == null) {
            throw new ProductNullException(resolve("product.category.null"), "product.category.null");
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
                .map(product -> new ProductResponse(
                        product.getId(),
                        product.getName(),
                        product.getDescription(),
                        product.getAvailableQuantity(),
                        product.getPrice(),
                        product.getCategory().getId(),
                        product.getCategory().getName(),
                        product.getCategory().getDescription(),
                        product.getCreatedBy(),
                        product.getImageUrl()))
                .toList();
    }


    public ProductResponse toProductResp(Product product) {
        if (product.getCategory() == null) {
            throw new ProductNullException("Product category must not be null", "product.category.null");
        }
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getAvailableQuantity(),
                product.getPrice(),
                product.getCategory().getId(),
                product.getCategory().getName(),
                product.getCategory().getDescription(),
                product.getCreatedBy(),
                product.getImageUrl());
    }

    protected ProductResponse fromProduct(Product product) {
        if (product == null) {
            log.error("is null{}", product);
            throw new ProductBadRequestException(resolve("product.null"));
        }
        if (product.getCategory() == null) {
            throw new ProductNullException(resolve("product.category.null"), "product.category.null");
        }
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getAvailableQuantity(),
                product.getPrice(),
                product.getCategory().getId(),
                product.getCategory().getName(),
                product.getCategory().getDescription(),
                product.getCreatedBy(),
                product.getImageUrl());
    }

    public ProductPurchaseResponse toproductPurchaseResponse(Product product, double quantity) {
        return new ProductPurchaseResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                quantity
        );
    }

    public Product toProduct(ProductRequest request) {
        return Product.builder()
                .id(request.id())
                .name(request.name())
                .description(request.description())
                .availableQuantity(request.availableQuantity())
                .price(request.price())
                .category(
                        request.categoryId() != null
                                ? Category.builder().id(request.categoryId()).build()
                                : null
                )
                .build();
    }

    public ProductResponse toProductResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getAvailableQuantity(),
                product.getPrice(),
                product.getCategory().getId(),
                product.getCategory().getName(),
                product.getCategory().getDescription(),
                product.getCreatedBy(),
                product.getImageUrl()
        );
    }

    /**
     * Resolves a message key from messages.properties with optional arguments.
     *
     * @param key the message key
     * @param args optional message arguments for formatting
     * @return the resolved message string
     */
    private String resolve(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }
}
