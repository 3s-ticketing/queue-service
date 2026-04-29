package org.ticketing.queue.domain.repository;

import org.ticketing.queue.domain.model.BannedUser;

import java.util.UUID;

public interface BannedUserRepository {
    BannedUser save(BannedUser bannedUser);

    boolean existsByQueueIdAndUserId(UUID queueId, UUID userId);
}
