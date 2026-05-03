package org.ticketing.queue.domain.event;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MatchApprovedEvent(
        UUID matchId,
        OffsetDateTime ticketOpenAt
) {
}