package com.nisatsama.shelfsense.inventory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByProductIdAndLocationId(
            Long productId,
            Long locationId
    );

    boolean existsByProductIdAndLocationId(
            Long productId,
            Long locationId
    );
}