package org.ticketing.queue.presentation.dto.request;

import org.ticketing.queue.application.dto.query.QueueListQuery;
import org.ticketing.queue.domain.model.QueueStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record QueueListGetRequest(
        UUID matchId,
        Integer maxActiveUsers,
        QueueStatus status,
        LocalDateTime openAtFrom,
        LocalDateTime openAtTo,
        Boolean isDeleted
) {
    public static QueueListQuery toQuery(QueueListGetRequest request) {
        return new QueueListQuery(
                request.matchId,
                request.maxActiveUsers,
                request.status,
                request.openAtFrom,
                request.openAtTo,
                request.isDeleted
        );
    }
}
