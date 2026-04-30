package org.ticketing.queue.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.ticketing.queue.domain.model.BannedUser;

import java.util.UUID;

public interface BannedUserJpaRepository extends JpaRepository<BannedUser, UUID> {
    boolean existsByMatchIdAndUserId(UUID queueId, UUID userId);
}