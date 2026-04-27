package org.ticketing.queue.presentation.dto.response;

import java.util.UUID;

public record QueueIdResponse(
        UUID queueId
) {
    public static QueueIdResponse from(UUID queueId) {
        return new QueueIdResponse(queueId);
    }
}
