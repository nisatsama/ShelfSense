package com.nisatsama.shelfsense.reservation;

import com.nisatsama.shelfsense.inventory.Inventory;
import com.nisatsama.shelfsense.inventory.InventoryRepository;
import com.nisatsama.shelfsense.inventory.InventoryTransaction;
import com.nisatsama.shelfsense.inventory.InventoryTransactionRepository;
import com.nisatsama.shelfsense.inventory.InventoryTransactionType;
import com.nisatsama.shelfsense.location.Location;
import com.nisatsama.shelfsense.location.LocationRepository;
import com.nisatsama.shelfsense.product.Product;
import com.nisatsama.shelfsense.product.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@Transactional
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final Clock clock;

    public ReservationService(
            ReservationRepository reservationRepository,
            InventoryRepository inventoryRepository,
            InventoryTransactionRepository inventoryTransactionRepository,
            ProductRepository productRepository,
            LocationRepository locationRepository,
            Clock clock
    ) {
        this.reservationRepository = reservationRepository;
        this.inventoryRepository = inventoryRepository;
        this.inventoryTransactionRepository = inventoryTransactionRepository;
        this.productRepository = productRepository;
        this.locationRepository = locationRepository;
        this.clock = clock;
    }

    public Reservation create(
            Long productId,
            Long locationId,
            int quantity
    ) {

        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "Reservation quantity must be greater than zero"
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

        Inventory inventory = inventoryRepository
                .findByProductIdAndLocationId(productId, locationId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Inventory not found for product "
                                        + productId
                                        + " at location "
                                        + locationId
                        )
                );

        if (inventory.getAvailable() < quantity) {
            throw new IllegalArgumentException(
                    "Insufficient inventory. Available: "
                            + inventory.getAvailable()
                            + ", requested: "
                            + quantity
            );
        }

        inventory.setReserved(
                inventory.getReserved() + quantity
        );

        Reservation reservation = new Reservation();
        reservation.setProduct(product);
        reservation.setLocation(location);
        reservation.setQuantity(quantity);
        reservation.setStatus(ReservationStatus.ACTIVE);
        LocalDateTime createdAt = LocalDateTime.now(clock);
        reservation.setCreatedAt(createdAt);
        reservation.setExpiresAt(createdAt.plusMinutes(10));

        inventoryRepository.save(inventory);
        Reservation created = reservationRepository.save(reservation);
        inventoryTransactionRepository.append(InventoryTransaction.create(
                inventory,
                InventoryTransactionType.RESERVATION_CREATED,
                0,
                quantity,
                created.getId()
        ));

        return created;
    }

    @Transactional(readOnly = true)
    public Reservation findById(Long id) {
        return reservationRepository.findWithProductAndLocationById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Reservation not found: " + id
                        )
                );
    }

    public Reservation release(Long reservationId) {

        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Reservation not found: " + reservationId
                        )
                );

        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            throw new IllegalArgumentException(
                    "Only ACTIVE reservations can be released"
            );
        }
        ensureNotExpired(reservation);

        Inventory inventory = inventoryRepository
                .findByProductIdAndLocationId(
                        reservation.getProduct().getId(),
                        reservation.getLocation().getId()
                )
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Inventory not found for reservation: "
                                        + reservationId
                        )
                );

        inventory.setReserved(
                inventory.getReserved() - reservation.getQuantity()
        );

        reservation.setStatus(ReservationStatus.RELEASED);

        inventoryRepository.save(inventory);
        Reservation released = reservationRepository.save(reservation);
        inventoryTransactionRepository.append(InventoryTransaction.create(
                inventory,
                InventoryTransactionType.RESERVATION_RELEASED,
                0,
                -reservation.getQuantity(),
                reservation.getId()
        ));

        return released;
    }

    public Reservation confirm(Long reservationId) {

        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Reservation not found: " + reservationId
                        )
                );

        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            throw new IllegalArgumentException(
                    "Only ACTIVE reservations can be confirmed"
            );
        }
        ensureNotExpired(reservation);

        Inventory inventory = inventoryRepository
                .findByProductIdAndLocationId(
                        reservation.getProduct().getId(),
                        reservation.getLocation().getId()
                )
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Inventory not found for reservation: "
                                        + reservationId
                        )
                );

        if (inventory.getReserved() < reservation.getQuantity()) {
            throw new IllegalArgumentException(
                    "Reserved inventory is insufficient for confirmation"
            );
        }

        if (inventory.getOnHand() < reservation.getQuantity()) {
            throw new IllegalArgumentException(
                    "On-hand inventory is insufficient for confirmation"
            );
        }

        inventory.setOnHand(
                inventory.getOnHand() - reservation.getQuantity()
        );

        inventory.setReserved(
                inventory.getReserved() - reservation.getQuantity()
        );

        reservation.setStatus(ReservationStatus.CONFIRMED);

        inventoryRepository.save(inventory);
        Reservation confirmed = reservationRepository.save(reservation);
        inventoryTransactionRepository.append(InventoryTransaction.create(
                inventory,
                InventoryTransactionType.RESERVATION_CONFIRMED,
                -reservation.getQuantity(),
                -reservation.getQuantity(),
                reservation.getId()
        ));

        return confirmed;
    }

    private void ensureNotExpired(Reservation reservation) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (reservation.getExpiresAt() == null || !reservation.getExpiresAt().isAfter(now)) {
            throw new IllegalArgumentException("Reservation has expired");
        }
    }
}
