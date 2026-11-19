package com.nisatsama.shelfsense.product;

import com.nisatsama.shelfsense.product.dto.ProductCreateRequest;
import com.nisatsama.shelfsense.product.dto.ProductResponse;
import com.nisatsama.shelfsense.product.dto.ProductUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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
    @PreAuthorize("hasRole('ADMIN')")
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

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ProductResponse update(
            @PathVariable Long id,
            @Valid @RequestBody ProductUpdateRequest request
    ) {
        Product updated = productService.update(
                id,
                request.name(),
                request.category(),
                request.unitPrice(),
                request.active()
        );
        return toResponse(updated);
    }

    @GetMapping
    public List<ProductResponse> findAll(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean active
    ) {
        return productService.search(q, category, active)
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
