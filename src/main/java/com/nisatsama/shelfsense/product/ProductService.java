package com.nisatsama.shelfsense.product;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

    @Transactional(readOnly = true)
    public List<Product> findAll() {
        return productRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Product findById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Product not found: " + id
                        )
                );
    }
}