package org.ticketing.queue.infrastructure.persistence;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class SseEmitterRepository {

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public void save(UUID matchId, UUID userId, SseEmitter emitter) {
        emitters.put(buildKey(matchId, userId), emitter);
    }

    public SseEmitter find(UUID matchId, UUID userId) {
        return emitters.get(buildKey(matchId, userId));
    }

    public void remove(UUID matchId, UUID userId) {
        emitters.remove(buildKey(matchId, userId));
    }

    public List<UUID> findUserIdsByMatchId(UUID matchId) {
        String prefix = matchId + ":";
        return emitters.keySet().stream()
                .filter(k -> k.startsWith(prefix))
                .map(k -> UUID.fromString(k.split(":")[1]))
                .toList();
    }

    private String buildKey(UUID matchId, UUID userId) {
        return matchId + ":" + userId;
    }

    public Set<UUID> getAllMatchIds() {
        return emitters.keySet().stream()
                .map(key -> UUID.fromString(key.split(":")[0]))
                .collect(Collectors.toSet());
    }
}
