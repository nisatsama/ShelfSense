package com.nisatsama.shelfsense.procurement;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GoodsReceiptRepository extends JpaRepository<GoodsReceipt, Long> {

    @EntityGraph(attributePaths = {"purchaseOrder", "items", "items.product", "items.purchaseOrderItem"})
    Optional<GoodsReceipt> findByIdempotencyKey(String idempotencyKey);

    @EntityGraph(attributePaths = {"purchaseOrder", "items", "items.product", "items.purchaseOrderItem"})
    List<GoodsReceipt> findByPurchaseOrderIdOrderByReceivedAtDescIdDesc(Long purchaseOrderId);
}
