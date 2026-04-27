package org.ticketing.queue.domain.dto;

import org.ticketing.queue.domain.model.QueueStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record QueueSearchCondition(
        UUID matchId,
        Integer maxActiveUsers,
        QueueStatus status,
        LocalDateTime openAtFrom,
        LocalDateTime openAtTo,
        Boolean isDeleted
) {
}
