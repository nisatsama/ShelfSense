package com.nisatsama.shelfsense.inventory;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InventoryAdjustmentRepository extends JpaRepository<InventoryAdjustment, Long> {

    @EntityGraph(attributePaths = {"inventory", "inventory.product", "inventory.location", "transaction"})
    Optional<InventoryAdjustment> findByIdempotencyKey(String idempotencyKey);

    @EntityGraph(attributePaths = {"inventory", "inventory.product", "inventory.location", "transaction"})
    List<InventoryAdjustment> findAllByOrderByCreatedAtDescIdDesc();

    @EntityGraph(attributePaths = {"inventory", "inventory.product", "inventory.location", "transaction"})
    List<InventoryAdjustment> findByInventoryIdOrderByCreatedAtDescIdDesc(Long inventoryId);
}
