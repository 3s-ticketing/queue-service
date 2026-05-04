package org.ticketing.queue.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.ticketing.queue.domain.model.Queue;
import org.ticketing.queue.domain.model.QueueStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaQueueRepository extends JpaRepository<Queue, UUID> {
    Optional<Queue> findByIdAndDeletedAtIsNull(UUID queueId);

    Optional<Queue> findByMatchIdAndDeletedAtIsNull(UUID matchId);

    boolean existsByMatchIdAndDeletedAtIsNull(UUID matchId);

    @Query("SELECT q FROM Queue q WHERE q.status = :status AND q.openAt < :openAt AND q.deletedAt IS NULL")
    List<Queue> findAllReadyToOpen(@Param("status") QueueStatus status, @Param("openAt") LocalDateTime openAt);

    @Query("SELECT q FROM Queue q WHERE q.status = :status AND q.expiredAt < :expiredAt AND q.deletedAt IS NULL")
    List<Queue> findAllExpired(@Param("status") QueueStatus status, @Param("expiredAt") LocalDateTime expiredAt);
}
