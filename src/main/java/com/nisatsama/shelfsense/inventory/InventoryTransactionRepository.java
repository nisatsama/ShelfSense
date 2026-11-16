package com.nisatsama.shelfsense.inventory;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public class InventoryTransactionRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public InventoryTransaction append(InventoryTransaction transaction) {
        entityManager.persist(transaction);
        return transaction;
    }

    @Transactional(readOnly = true)
    public List<InventoryTransaction> findByInventoryIdOrderByCreatedAtAscIdAsc(Long inventoryId) {
        return entityManager.createQuery(
                        "select transaction from InventoryTransaction transaction "
                                + "where transaction.inventory.id = :inventoryId "
                                + "order by transaction.createdAt asc, transaction.id asc",
                        InventoryTransaction.class
                )
                .setParameter("inventoryId", inventoryId)
                .getResultList();
    }

    @Transactional(readOnly = true)
    public List<InventoryTransaction> findByReservationId(Long reservationId) {
        return entityManager.createQuery(
                        "select transaction from InventoryTransaction transaction "
                                + "where transaction.reservationId = :reservationId "
                                + "order by transaction.createdAt asc, transaction.id asc",
                        InventoryTransaction.class
                )
                .setParameter("reservationId", reservationId)
                .getResultList();
    }
}
