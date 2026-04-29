package org.ticketing.queue.infrastructure.redis.pubsub;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class QueueRedisPublisher {

    private final StringRedisTemplate redisTemplate;
    private static final String CHANNEL = "queue-channel";

    public void publish(UUID matchId) {
        redisTemplate.convertAndSend(CHANNEL, matchId.toString());
    }
}