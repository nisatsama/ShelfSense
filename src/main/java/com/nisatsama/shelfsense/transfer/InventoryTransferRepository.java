package com.nisatsama.shelfsense.transfer;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InventoryTransferRepository extends JpaRepository<InventoryTransfer, Long> {

    @EntityGraph(attributePaths = {"product", "sourceLocation", "destinationLocation"})
    List<InventoryTransfer> findAllByOrderByCreatedAtDescIdDesc();

    @EntityGraph(attributePaths = {"product", "sourceLocation", "destinationLocation"})
    List<InventoryTransfer> findByStatusOrderByCreatedAtDescIdDesc(TransferStatus status);

    @EntityGraph(attributePaths = {"product", "sourceLocation", "destinationLocation"})
    Optional<InventoryTransfer> findDetailedById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from InventoryTransfer t "
            + "join fetch t.product "
            + "join fetch t.sourceLocation "
            + "join fetch t.destinationLocation "
            + "where t.id = :id")
    Optional<InventoryTransfer> findByIdForUpdate(@Param("id") Long id);

    @Query("select coalesce(sum(t.dispatchedQuantity - t.receivedQuantity - t.damagedQuantity), 0) "
            + "from InventoryTransfer t "
            + "where t.product.id = :productId "
            + "and t.destinationLocation.id = :destinationLocationId "
            + "and t.status in :statuses")
    int sumIncomingInTransit(
            @Param("productId") Long productId,
            @Param("destinationLocationId") Long destinationLocationId,
            @Param("statuses") Collection<TransferStatus> statuses
    );

    @Query("select coalesce(sum(t.requestedQuantity), 0) "
            + "from InventoryTransfer t "
            + "where t.product.id = :productId "
            + "and t.destinationLocation.id = :destinationLocationId "
            + "and t.status in :statuses")
    int sumPendingRequested(
            @Param("productId") Long productId,
            @Param("destinationLocationId") Long destinationLocationId,
            @Param("statuses") Collection<TransferStatus> statuses
    );

    long countByStatusIn(Collection<TransferStatus> statuses);
}
