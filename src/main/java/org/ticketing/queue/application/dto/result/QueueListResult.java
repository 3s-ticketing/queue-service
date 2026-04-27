package org.ticketing.queue.application.dto.result;

import org.springframework.data.domain.Page;
import org.ticketing.queue.domain.dto.QueueProjection;

import java.util.List;

public record QueueListResult(
        List<QueueResult> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
    public static QueueListResult from(Page<QueueProjection> page) {
        List<QueueResult> content = page.getContent()
                .stream()
                .map(QueueResult::from)
                .toList();

        return new QueueListResult(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext()
        );
    }
}