package org.ticketing.queue.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.ticketing.queue.domain.dto.QueueProjection;
import org.ticketing.queue.domain.dto.QueueSearchCondition;
import org.ticketing.queue.domain.model.Queue;
import org.ticketing.queue.domain.model.QueueStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface QueueRepository {
    Queue findById(UUID queueId);

    Queue findByMatchId(UUID matchId);

    Queue findByIdAndDeletedAtIsNull(UUID queueId);

    Page<QueueProjection> findAllByCondition(QueueSearchCondition condition, Pageable pageable);

    Queue save(Queue queue);

    boolean existsByMatchId(UUID matchId);

    List<Queue> findAllByStatusAndOpenAtBefore(QueueStatus status, LocalDateTime openAt);

    List<Queue> findAllByStatusAndExpiredAtBefore(QueueStatus status, LocalDateTime expiredAt);
}
