package com.nisatsama.shelfsense.reservation;

import com.nisatsama.shelfsense.inventory.Inventory;
import com.nisatsama.shelfsense.inventory.InventoryRepository;
import com.nisatsama.shelfsense.location.Location;
import com.nisatsama.shelfsense.location.LocationRepository;
import com.nisatsama.shelfsense.product.Product;
import com.nisatsama.shelfsense.product.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;

    public ReservationService(
            ReservationRepository reservationRepository,
            InventoryRepository inventoryRepository,
            ProductRepository productRepository,
            LocationRepository locationRepository
    ) {
        this.reservationRepository = reservationRepository;
        this.inventoryRepository = inventoryRepository;
        this.productRepository = productRepository;
        this.locationRepository = locationRepository;
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

        inventoryRepository.save(inventory);

        return reservationRepository.save(reservation);
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

        Reservation reservation = reservationRepository.findById(reservationId)
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

        return reservationRepository.save(reservation);
    }
}