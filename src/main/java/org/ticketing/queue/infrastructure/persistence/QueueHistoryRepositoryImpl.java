package org.ticketing.queue.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.ticketing.queue.domain.model.QueueHistory;
import org.ticketing.queue.domain.repository.QueueHistoryRepository;

@Repository
@RequiredArgsConstructor
public class QueueHistoryRepositoryImpl implements QueueHistoryRepository {

    private final JpaQueueHistoryRepository jpaQueueHistoryRepository;

    @Override
    public QueueHistory save(QueueHistory queueHistory) {
        return jpaQueueHistoryRepository.save(queueHistory);
    }
}
