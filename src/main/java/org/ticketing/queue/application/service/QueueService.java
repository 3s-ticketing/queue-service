package org.ticketing.queue.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ticketing.queue.application.dto.command.QueueCreateCommand;
import org.ticketing.queue.application.dto.command.QueueUpdateCommand;
import org.ticketing.queue.application.dto.query.QueueListQuery;
import org.ticketing.queue.application.dto.result.QueueListResult;
import org.ticketing.queue.application.dto.result.QueueResult;
import org.ticketing.queue.domain.dto.QueueProjection;
import org.ticketing.queue.domain.model.Queue;
import org.ticketing.queue.domain.repository.QueueRepository;

import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class QueueService {

    private final QueueRepository queueRepository;

    @Transactional(readOnly = true)
    public QueueResult getQueue(UUID queueId) {
        return QueueResult.from(queueRepository.findById(queueId));
    }

    @Transactional(readOnly = true)
    public QueueListResult getQueueList(QueueListQuery query, Pageable pageable) {
        Page<QueueProjection> projections = queueRepository
                .findAllByCondition(QueueListQuery.toQueueSearchCondition(query), pageable);

        return QueueListResult.from(projections);
    }

    public UUID createQueue(QueueCreateCommand command) {
        Queue queue = Queue.create(
                UUID.randomUUID(),
                command.matchId(),
                command.maxActiveUsers(),
                command.status(),
                command.openAt()
        );

        return queueRepository.save(queue).getId();
    }

    public void updateQueue(UUID queueId, QueueUpdateCommand command) {
        Queue queue = queueRepository.findByIdAndDeletedAtIsNull(queueId);
        queue.update(
                command.matchId(),
                command.maxActiveUsers(),
                command.status(),
                command.openAt()
        );
    }

    public void deleteQueue(UUID queueId, UUID userId) {
        Queue queue = queueRepository.findByIdAndDeletedAtIsNull(queueId);
        queue.softDelete(userId);
    }

}
