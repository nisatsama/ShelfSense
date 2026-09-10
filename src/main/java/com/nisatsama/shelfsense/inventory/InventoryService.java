package com.nisatsama.shelfsense.inventory;

import com.nisatsama.shelfsense.location.Location;
import com.nisatsama.shelfsense.location.LocationRepository;
import com.nisatsama.shelfsense.product.Product;
import com.nisatsama.shelfsense.product.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;

    public InventoryService(
            InventoryRepository inventoryRepository,
            ProductRepository productRepository,
            LocationRepository locationRepository
    ) {
        this.inventoryRepository = inventoryRepository;
        this.productRepository = productRepository;
        this.locationRepository = locationRepository;
    }

    public Inventory create(Long productId, Long locationId, int onHand) {

        if (onHand < 0) {
            throw new IllegalArgumentException(
                    "On-hand quantity cannot be negative"
            );
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Product not found: " + productId
                        )
                );

        Location location = locationRepository.findById(locationId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Location not found: " + locationId
                        )
                );

        if (inventoryRepository.existsByProductIdAndLocationId(
                productId,
                locationId
        )) {
            throw new IllegalArgumentException(
                    "Inventory already exists for this product and location"
            );
        }

        Inventory inventory = new Inventory();
        inventory.setProduct(product);
        inventory.setLocation(location);
        inventory.setOnHand(onHand);
        inventory.setReserved(0);

        return inventoryRepository.save(inventory);
    }

    @Transactional(readOnly = true)
    public Inventory findByProductAndLocation(
            Long productId,
            Long locationId
    ) {
        return inventoryRepository
                .findByProductIdAndLocationId(productId, locationId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Inventory not found for product "
                                        + productId
                                        + " at location "
                                        + locationId
                        )
                );
    }
}