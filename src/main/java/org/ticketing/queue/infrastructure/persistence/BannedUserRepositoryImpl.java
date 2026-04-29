package org.ticketing.queue.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.ticketing.queue.domain.model.BannedUser;
import org.ticketing.queue.domain.repository.BannedUserRepository;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class BannedUserRepositoryImpl implements BannedUserRepository {

    private final BannedUserJpaRepository bannedUserJpaRepository;

    @Override
    public BannedUser save(BannedUser bannedUser) {
        return bannedUserJpaRepository.save(bannedUser);
    }

    @Override
    public boolean existsByQueueIdAndUserId(UUID matchId, UUID userId) {
        return bannedUserJpaRepository.existsByMatchIdAndUserId(matchId, userId);
    }
}
