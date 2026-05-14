package org.ticketing.queue.infrastructure.persistence;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SseEmitterRepository {

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    // matchId → userId Set 역방향 인덱스 (O(1) 조회)
    private final Map<UUID, Set<UUID>> matchUserIndex = new ConcurrentHashMap<>();

    public void save(UUID matchId, UUID userId, SseEmitter emitter) {
        emitters.put(buildKey(matchId, userId), emitter);
        matchUserIndex.computeIfAbsent(matchId, k -> ConcurrentHashMap.newKeySet()).add(userId);
    }

    public SseEmitter find(UUID matchId, UUID userId) {
        return emitters.get(buildKey(matchId, userId));
    }

    public void remove(UUID matchId, UUID userId) {
        emitters.remove(buildKey(matchId, userId));
        matchUserIndex.computeIfPresent(matchId, (id, userIds) -> {
            userIds.remove(userId);
            return userIds.isEmpty() ? null : userIds;
        });
    }

    public List<UUID> findUserIdsByMatchId(UUID matchId) {
        Set<UUID> userIds = matchUserIndex.get(matchId);
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyList();
        }
        return List.copyOf(userIds);
    }

    public Set<UUID> getAllMatchIds() {
        return matchUserIndex.keySet();
    }

    private String buildKey(UUID matchId, UUID userId) {
        return matchId + ":" + userId;
    }
}
