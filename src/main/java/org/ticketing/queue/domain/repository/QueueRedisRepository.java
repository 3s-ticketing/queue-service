package org.ticketing.queue.domain.repository;

import java.time.LocalDateTime;
import java.util.UUID;

public interface QueueRedisRepository {

    // ── Sorted Set (대기열) ───────────────────────────────────────────────

    boolean entry(UUID matchId, UUID userId);

    Long getRank(UUID matchId, UUID userId);

    Long getTotalCount(UUID matchId);

    boolean exists(UUID matchId, UUID userId);

    LocalDateTime getEnteredAt(UUID matchId, UUID userId);

    void exit(UUID matchId, UUID userId);

    // ── 슬롯 관리 ────────────────────────────────────────────────────────

    void initSlots(UUID matchId);

    Long getAvailableSlots(UUID matchId);

    boolean acquireSlot(UUID matchId);

    void releaseSlot(UUID matchId);

    // ── 통과 토큰 관리 ───────────────────────────────────────────────────

    boolean acquirePassToken(UUID matchId, UUID userId);

    String getPassToken(UUID matchId, UUID userId);

    void savePassToken(UUID matchId, UUID userId, String token);

    void deletePassToken(UUID matchId, UUID userId);

    String findPassToken(UUID matchId, UUID userId);

    LocalDateTime getExpiredAt(UUID matchId, UUID userId);
}