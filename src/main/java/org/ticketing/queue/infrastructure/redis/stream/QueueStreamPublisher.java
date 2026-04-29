package org.ticketing.queue.infrastructure.redis.stream;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class QueueStreamPublisher {

    private final StringRedisTemplate redisTemplate;
    private static final String STREAM_KEY = "queue-events";

    public void publishSlotReleased(UUID matchId) {
        Map<String, String> fields = new HashMap<>();
        fields.put("matchId", matchId.toString());
        fields.put("type", "SLOT_RELEASED");
        fields.put("timestamp", String.valueOf(System.currentTimeMillis()));

        redisTemplate.opsForStream().add(STREAM_KEY, fields);
    }
}
