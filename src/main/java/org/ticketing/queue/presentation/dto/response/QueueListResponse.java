package org.ticketing.queue.presentation.dto.response;

import org.ticketing.queue.application.dto.result.QueueListResult;

import java.util.List;

public record QueueListResponse(
        List<QueueResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
    public static QueueListResponse from(QueueListResult result) {
        List<QueueResponse> queues = result.content()
                .stream()
                .map(QueueResponse::from)
                .toList();

        return new QueueListResponse(
                queues,
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages(),
                result.hasNext()
        );
    }
}
