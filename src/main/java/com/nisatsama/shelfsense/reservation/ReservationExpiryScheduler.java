package com.nisatsama.shelfsense.reservation;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "shelfsense.reservations.expiry.scheduling-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class ReservationExpiryScheduler {

    private final ReservationExpiryProcessor expiryProcessor;

    public ReservationExpiryScheduler(ReservationExpiryProcessor expiryProcessor) {
        this.expiryProcessor = expiryProcessor;
    }

    @Scheduled(fixedDelayString = "${shelfsense.reservations.expiry.fixed-delay:PT30S}")
    public void processDueReservations() {
        expiryProcessor.processDueBatch();
    }
}
