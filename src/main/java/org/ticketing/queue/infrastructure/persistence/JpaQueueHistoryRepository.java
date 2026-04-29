package org.ticketing.queue.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.ticketing.queue.domain.model.QueueHistory;

import java.util.UUID;

public interface JpaQueueHistoryRepository extends JpaRepository<QueueHistory, UUID> {
}
