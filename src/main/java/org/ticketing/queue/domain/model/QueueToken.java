package org.ticketing.queue.domain.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class QueueToken {
    private final UUID matchId;
    private final UUID userId;
    private final String token;
    private final LocalDateTime expiredAt;

    public static QueueToken of(UUID matchId, UUID userId, String value, LocalDateTime expiredAt) {
        return new QueueToken(matchId, userId, value, expiredAt);
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiredAt);
    }
}
