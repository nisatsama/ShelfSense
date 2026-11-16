package com.nisatsama.shelfsense.reservation;

import com.nisatsama.shelfsense.inventory.Inventory;
import com.nisatsama.shelfsense.inventory.InventoryRepository;
import com.nisatsama.shelfsense.inventory.InventoryTransaction;
import com.nisatsama.shelfsense.inventory.InventoryTransactionRepository;
import com.nisatsama.shelfsense.inventory.InventoryTransactionType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class ReservationExpiryService {

    private final ReservationRepository reservationRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final Clock clock;

    public ReservationExpiryService(
            ReservationRepository reservationRepository,
            InventoryRepository inventoryRepository,
            InventoryTransactionRepository inventoryTransactionRepository,
            Clock clock
    ) {
        this.reservationRepository = reservationRepository;
        this.inventoryRepository = inventoryRepository;
        this.inventoryTransactionRepository = inventoryTransactionRepository;
        this.clock = clock;
    }

    @Transactional
    public boolean expireIfDue(Long reservationId) {
        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId).orElse(null);
        if (reservation == null || reservation.getStatus() != ReservationStatus.ACTIVE) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (reservation.getExpiresAt() == null || reservation.getExpiresAt().isAfter(now)) {
            return false;
        }

        Inventory inventory = inventoryRepository.findByProductIdAndLocationId(
                        reservation.getProduct().getId(), reservation.getLocation().getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Inventory not found for reservation: " + reservationId));
        if (inventory.getReserved() < reservation.getQuantity()) {
            throw new IllegalStateException(
                    "Reserved inventory is insufficient for expiration: " + reservationId);
        }

        inventory.setReserved(inventory.getReserved() - reservation.getQuantity());
        reservation.setStatus(ReservationStatus.EXPIRED);
        inventoryRepository.save(inventory);
        reservationRepository.save(reservation);
        inventoryTransactionRepository.append(InventoryTransaction.create(
                inventory,
                InventoryTransactionType.RESERVATION_EXPIRED,
                0,
                -reservation.getQuantity(),
                reservation.getId()
        ));
        return true;
    }
}
