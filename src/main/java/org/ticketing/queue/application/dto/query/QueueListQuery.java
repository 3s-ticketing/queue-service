package org.ticketing.queue.application.dto.query;

import org.ticketing.queue.domain.dto.QueueSearchCondition;
import org.ticketing.queue.domain.model.QueueStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record QueueListQuery(
        UUID matchId,
        Integer maxActiveUsers,
        QueueStatus status,
        LocalDateTime openAtFrom,
        LocalDateTime openAtTo,
        Boolean isDeleted
) {
    public static QueueSearchCondition toQueueSearchCondition(QueueListQuery query) {
        return new QueueSearchCondition(
                query.matchId,
                query.maxActiveUsers,
                query.status,
                query.openAtFrom,
                query.openAtTo,
                query.isDeleted
        );
    }
}
