package com.nisatsama.shelfsense.reservation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReservationExpiryProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReservationExpiryProcessor.class);
    private static final int BATCH_SIZE = 100;

    private final ReservationRepository reservationRepository;
    private final ReservationExpiryService expiryService;
    private final Clock clock;

    // A scalar keyset cursor is sufficient; due reservations remain in PostgreSQL.
    // Synchronization prevents overlapping scheduler calls from racing this cursor.
    private long cursor;

    public ReservationExpiryProcessor(
            ReservationRepository reservationRepository,
            ReservationExpiryService expiryService,
            Clock clock
    ) {
        this.reservationRepository = reservationRepository;
        this.expiryService = expiryService;
        this.clock = clock;
    }

    /** Poll one bounded page; each mutation enters its own service transaction. */
    public synchronized int processDueBatch() {
        LocalDateTime pollTime = LocalDateTime.now(clock);
        List<Long> dueIds = reservationRepository.findDueReservationIdsAfter(
                pollTime, cursor, PageRequest.of(0, BATCH_SIZE));
        if (dueIds.isEmpty() && cursor != 0L) {
            // The later key range is drained; wrap and rediscover remaining due rows.
            dueIds = reservationRepository.findDueReservationIdsAfter(
                    pollTime, 0L, PageRequest.of(0, BATCH_SIZE));
        }
        if (dueIds.isEmpty()) {
            cursor = 0L;
            return 0;
        }

        // Advance before processing so a failing reservation cannot pin the next poll.
        cursor = dueIds.getLast();
        int expired = 0;
        for (Long reservationId : dueIds) {
            try {
                if (expiryService.expireIfDue(reservationId)) {
                    expired++;
                }
            } catch (RuntimeException exception) {
                LOGGER.error("Failed to process expiry for reservation {}", reservationId, exception);
            }
        }
        return expired;
    }
}
