package org.ticketing.queue.domain.event;

import java.util.UUID;

public record MatchApprovedEvent(
        UUID matchId
) {
}