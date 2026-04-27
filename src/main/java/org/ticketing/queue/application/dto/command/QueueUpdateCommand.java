package org.ticketing.queue.application.dto.command;

import org.ticketing.queue.domain.model.QueueStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record QueueUpdateCommand(
        UUID matchId,
        Integer maxActiveUsers,
        QueueStatus status,
        LocalDateTime openAt
) {
}
