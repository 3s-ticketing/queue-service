package org.ticketing.queue.domain.event;

public record SlotsReleaseEvent(
        String reservationId,
        String matchId
) {
}