package org.ticketing.queue.domain.event;

public record SlotsReleaseEvent(
        String matchId,
        String userId
) {
}