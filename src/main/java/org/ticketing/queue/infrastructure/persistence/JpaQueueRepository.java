package org.ticketing.queue.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.ticketing.queue.domain.model.Queue;

import java.util.Optional;
import java.util.UUID;

public interface JpaQueueRepository extends JpaRepository<Queue, UUID> {
    Optional<Queue> findByIdAndDeletedAtIsNull(UUID queueId);

    Optional<Queue> findByMatchId(UUID matchId);

    boolean existsByMatchId(UUID matchId);
}
