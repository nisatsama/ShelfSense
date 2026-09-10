package com.nisatsama.shelfsense.product;

import com.nisatsama.shelfsense.product.dto.ProductCreateRequest;
import com.nisatsama.shelfsense.product.dto.ProductResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(
            @Valid @RequestBody ProductCreateRequest request
    ) {
        Product product = new Product();

        product.setSku(request.sku());
        product.setName(request.name());
        product.setCategory(request.category());
        product.setUnitPrice(request.unitPrice());

        Product saved = productService.create(product);

        return toResponse(saved);
    }

    @GetMapping
    public List<ProductResponse> findAll() {
        return productService.findAll()
                .stream()
                .map(ProductController::toResponse)
                .toList();
    }

    @GetMapping("/{id}")
    public ProductResponse findById(@PathVariable Long id) {
        return toResponse(productService.findById(id));
    }

    private static ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getCategory(),
                product.getUnitPrice(),
                product.isActive()
        );
    }
}