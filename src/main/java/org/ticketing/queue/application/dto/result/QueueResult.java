package org.ticketing.queue.application.dto.result;

import org.ticketing.queue.domain.dto.QueueProjection;
import org.ticketing.queue.domain.model.Queue;
import org.ticketing.queue.domain.model.QueueStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record QueueResult (
        UUID id,
        UUID matchId,
        Integer maxActiveUsers,
        QueueStatus status,
        LocalDateTime openAt
) {
    public static QueueResult from(Queue queue) {
        return new QueueResult(
                queue.getId(),
                queue.getMatchId(),
                queue.getMaxActiveUsers(),
                queue.getStatus(),
                queue.getOpenAt()
        );
    }

    public static QueueResult from(QueueProjection projection) {
        return new QueueResult(
                projection.id(),
                projection.matchId(),
                projection.maxActiveUsers(),
                projection.status(),
                projection.openAt()
        );
    }
}