package org.ticketing.queue.domain.dto;

import org.ticketing.queue.domain.model.QueueStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record QueueProjection(
        UUID id,
        UUID matchId,
        Integer maxActiveUsers,
        QueueStatus status,
        LocalDateTime openAt
) {
}
