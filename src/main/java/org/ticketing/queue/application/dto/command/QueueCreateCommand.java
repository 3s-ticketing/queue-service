package org.ticketing.queue.application.dto.command;

import java.time.LocalDateTime;
import java.util.UUID;

public record QueueCreateCommand(
        UUID matchId,
        Integer maxActiveUsers,
        LocalDateTime openAt
) {
}
