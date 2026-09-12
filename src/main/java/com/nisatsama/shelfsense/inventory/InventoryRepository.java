package com.nisatsama.shelfsense.inventory;

//import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
//import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    @EntityGraph(attributePaths = {"product", "location"})
    //@Lock(LockModeType.OPTIMISTIC)
    Optional<Inventory> findByProductIdAndLocationId(
            Long productId,
            Long locationId
    );

    boolean existsByProductIdAndLocationId(
            Long productId,
            Long locationId
    );
}