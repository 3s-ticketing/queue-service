package org.ticketing.queue.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.ticketing.queue.domain.model.Queue;
import org.ticketing.queue.domain.model.QueueStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaQueueRepository extends JpaRepository<Queue, UUID> {
    Optional<Queue> findByIdAndDeletedAtIsNull(UUID queueId);

    Optional<Queue> findByMatchId(UUID matchId);

    boolean existsByMatchId(UUID matchId);

    List<Queue> findAllByStatusAndOpenAtBefore(QueueStatus status, LocalDateTime openAt);

    List<Queue> findAllByStatusAndExpiredAtBefore(QueueStatus status, LocalDateTime expiredAt);
}
