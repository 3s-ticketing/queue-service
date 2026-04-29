package org.ticketing.queue.domain.service;

import org.ticketing.queue.domain.model.QueueToken;

import java.util.UUID;

public interface QueueTokenDomainService {
    QueueToken issue(UUID matchId, UUID userId);
}
