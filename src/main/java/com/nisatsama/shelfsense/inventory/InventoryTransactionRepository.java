package com.nisatsama.shelfsense.inventory;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
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

    @Transactional(readOnly = true)
    public List<InventoryTransaction> findLedgerEntries(
            Long inventoryId,
            InventoryTransactionType type,
            int limit
    ) {
        StringBuilder jpql = new StringBuilder(
                "select t from InventoryTransaction t "
                        + "join fetch t.inventory inv "
                        + "join fetch inv.product "
                        + "join fetch inv.location "
                        + "where 1 = 1 "
        );
        if (inventoryId != null) {
            jpql.append("and inv.id = :inventoryId ");
        }
        if (type != null) {
            jpql.append("and t.type = :type ");
        }
        jpql.append("order by t.createdAt desc, t.id desc");

        TypedQuery<InventoryTransaction> query = entityManager.createQuery(jpql.toString(), InventoryTransaction.class);
        if (inventoryId != null) {
            query.setParameter("inventoryId", inventoryId);
        }
        if (type != null) {
            query.setParameter("type", type);
        }
        query.setMaxResults(Math.max(1, Math.min(limit, 500)));
        return query.getResultList();
    }
}
