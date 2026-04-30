package org.ticketing.queue.domain.repository;

import org.ticketing.queue.domain.model.AcquireResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface QueueRedisRepository {

    // ── Sorted Set (대기열) ───────────────────────────────────────────────

    boolean entry(UUID matchId, UUID userId);

    Long getRank(UUID matchId, UUID userId);

    Map<UUID, Long> getRankBatch(UUID matchId, List<UUID> userIds);

    Long getTotalCount(UUID matchId);

    LocalDateTime getEnteredAt(UUID matchId, UUID userId);

    void exit(UUID matchId, UUID userId);

    void refreshQueue(UUID matchId);

    void saveBannedUser(UUID matchId, UUID userId);

    // ── 슬롯 관리 ────────────────────────────────────────────────────────

    void initSlots(UUID matchId);

    Long getAvailableSlots(UUID matchId);

    void releaseSlot(UUID matchId);

    AcquireResult acquireSlotAndToken(UUID matchId, UUID userId);

    // ── 통과 토큰 관리 ───────────────────────────────────────────────────

    String getPassToken(UUID matchId, UUID userId);

    void savePassToken(UUID matchId, UUID userId, String token);

    void deletePassToken(UUID matchId, UUID userId);

    String findPassToken(UUID matchId, UUID userId);

    LocalDateTime getExpiredAt(UUID matchId, UUID userId);

}