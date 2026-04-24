package org.ticketing.queue.presentation.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import org.ticketing.queue.application.dto.result.QueueResult;

import java.time.LocalDateTime;
import java.util.UUID;

public record QueueResponse (
        UUID id,
        UUID matchId,
        Integer maxActiveUsers,
        String status,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime openAt
) {
    public static QueueResponse from(QueueResult result) {
        return new QueueResponse(
                result.id(),
                result.matchId(),
                result.maxActiveUsers(),
                result.status().name(),
                result.openAt()
        );
    }
}