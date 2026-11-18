package com.nisatsama.shelfsense.product;

import com.nisatsama.shelfsense.common.ResourceNotFoundException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

@Service
@Transactional
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Product create(Product product) {
        if (productRepository.existsBySku(product.getSku())) {
            throw new IllegalArgumentException(
                    "Product with SKU already exists: " + product.getSku()
            );
        }

        return productRepository.save(product);
    }

    public Product update(Long id, String name, String category, BigDecimal unitPrice, boolean active) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
        product.setName(name);
        product.setCategory(category);
        product.setUnitPrice(unitPrice);
        product.setActive(active);
        return productRepository.save(product);
    }

    @Transactional(readOnly = true)
    public List<Product> findAll() {
        return productRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
    }

    @Transactional(readOnly = true)
    public List<Product> search(String query, String category, Boolean active) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        String normalizedCategory = category == null ? "" : category.trim().toLowerCase(Locale.ROOT);
        return findAll().stream()
                .filter(p -> normalizedQuery.isEmpty()
                        || p.getSku().toLowerCase(Locale.ROOT).contains(normalizedQuery)
                        || p.getName().toLowerCase(Locale.ROOT).contains(normalizedQuery)
                        || p.getCategory().toLowerCase(Locale.ROOT).contains(normalizedQuery))
                .filter(p -> normalizedCategory.isEmpty()
                        || p.getCategory().toLowerCase(Locale.ROOT).equals(normalizedCategory))
                .filter(p -> active == null || p.isActive() == active)
                .toList();
    }

    @Transactional(readOnly = true)
    public Product findById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Product not found: " + id
                        )
                );
    }
}
