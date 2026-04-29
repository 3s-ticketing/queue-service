package org.ticketing.queue.domain.repository;

import org.ticketing.queue.domain.model.QueueHistory;

public interface QueueHistoryRepository {
    QueueHistory save(QueueHistory queueHistory);
}
