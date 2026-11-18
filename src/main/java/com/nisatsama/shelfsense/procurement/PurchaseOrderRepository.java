package com.nisatsama.shelfsense.procurement;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    @EntityGraph(attributePaths = {"supplier", "destinationLocation", "items", "items.product"})
    List<PurchaseOrder> findAllByOrderByCreatedAtDescIdDesc();

    @EntityGraph(attributePaths = {"supplier", "destinationLocation", "items", "items.product"})
    List<PurchaseOrder> findByStatusOrderByCreatedAtDescIdDesc(PurchaseOrderStatus status);

    @EntityGraph(attributePaths = {"supplier", "destinationLocation", "items", "items.product"})
    Optional<PurchaseOrder> findDetailedById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select po from PurchaseOrder po where po.id = :id")
    Optional<PurchaseOrder> findByIdForUpdate(@Param("id") Long id);

    @Query("select coalesce(sum(i.orderedQuantity - i.receivedQuantity), 0) "
            + "from PurchaseOrderItem i "
            + "where i.product.id = :productId "
            + "and i.purchaseOrder.destinationLocation.id = :locationId "
            + "and i.purchaseOrder.status in :statuses")
    int sumIncomingQuantity(
            @Param("productId") Long productId,
            @Param("locationId") Long locationId,
            @Param("statuses") Collection<PurchaseOrderStatus> statuses
    );

    long countByStatusIn(Collection<PurchaseOrderStatus> statuses);
}
