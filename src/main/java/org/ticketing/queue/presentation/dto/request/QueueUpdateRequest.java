package org.ticketing.queue.presentation.dto.request;

import org.ticketing.queue.application.dto.command.QueueUpdateCommand;
import org.ticketing.queue.domain.model.QueueStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record QueueUpdateRequest(
        UUID matchId,
        Integer maxActiveUsers,
        QueueStatus status,
        LocalDateTime openAt,
        LocalDateTime expiredAt
) {
    public static QueueUpdateCommand toCommand(QueueUpdateRequest request) {
        return new QueueUpdateCommand(
                request.matchId,
                request.maxActiveUsers,
                request.status,
                request.openAt,
                request.expiredAt
        );
    }
}
