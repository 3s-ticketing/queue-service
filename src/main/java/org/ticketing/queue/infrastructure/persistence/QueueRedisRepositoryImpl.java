package org.ticketing.queue.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import org.ticketing.queue.domain.exception.QueueException;
import org.ticketing.queue.domain.repository.QueueRedisRepository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Repository
@RequiredArgsConstructor
public class QueueRedisRepositoryImpl implements QueueRedisRepository {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String QUEUE_KEY = "queue:%s"; // queue:{matchId}
    private static final String SLOTS_AVAILABLE_KEY  = "queue:slots:available:%s";  // 남은 슬롯
    private static final String PASS_TOKEN_PREFIX = "queue:pass-token:";
    private static final String PLACEHOLDER = "PENDING";   // 토큰 발급 전 선점 표시
    private static final long TOKEN_TTL_MINUTES = 10L;

    // 유저 대기열 진입 (score = 진입 시각)
    @Override
    public boolean entry(UUID matchId, UUID userId) {
        String key = getKey(matchId);
        double score = System.currentTimeMillis();
        return Boolean.TRUE.equals(
                redisTemplate.opsForZSet()
                        .addIfAbsent(key, userId.toString(), score));
    }

    // 유저의 현재 순번 조회 (0부터 시작하므로 +1)
    @Override
    public Long getRank(UUID matchId, UUID userId) {
        String key = getKey(matchId);
        Long rank = redisTemplate.opsForZSet().rank(key, userId.toString());
        return rank != null ? rank + 1 : null;
    }

    // 전체 대기 인원수 조회
    @Override
    public Long getTotalCount(UUID matchId) {
        String key = getKey(matchId);
        return redisTemplate.opsForZSet().size(key);
    }

    // 유저가 대기열에 존재하는지 확인
    @Override
    public boolean exists(UUID matchId, UUID userId) {
        String key = getKey(matchId);
        Double score = redisTemplate.opsForZSet().score(key, userId.toString());
        return score != null;
    }

    // 유저 대기열 진입 시간 조회
    @Override
    public LocalDateTime getEnteredAt(UUID matchId, UUID userId) {
        String key = getKey(matchId);

        Double score = redisTemplate.opsForZSet().score(key, userId.toString());

        if (score == null) {
            throw new QueueException(
                    String.format("대기열에 존재하지 않는 유저입니다. matchId=%s, userId=%s", matchId, userId),
                    HttpStatus.NOT_FOUND
            );
        }

        return Instant.ofEpochMilli(score.longValue())
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
    }

    // 유저 대기열 이탈
    @Override
    public void exit(UUID matchId, UUID userId) {
        String key = getKey(matchId);
        redisTemplate.opsForZSet().remove(key, userId.toString());
    }

    @Override
    public void initSlots(UUID matchId, int maxActiveUsers) {

    }

    // 사용가능한 슬롯 수 확인
    @Override
    public Long getAvailableSlots(UUID matchId) {
        String value = redisTemplate.opsForValue().get(SLOTS_AVAILABLE_KEY.formatted(matchId));
        return value != null ? Long.parseLong(value) : 0L;
    }

    // 슬롯 수 차감
    @Override
    public boolean acquireSlot(UUID matchId) {
        Long remaining = redisTemplate.opsForValue().decrement(SLOTS_AVAILABLE_KEY.formatted(matchId));

        // 차감 후 0 이상이면 선점 성공, 음수면 실패 후 복구
        if (remaining < 0) {
            redisTemplate.opsForValue().increment(SLOTS_AVAILABLE_KEY.formatted(matchId));
            return false;
        }
        return true;
    }

    // 슬롯 반환
    @Override
    public void releaseSlot(UUID matchId) {
        redisTemplate.opsForValue().increment(SLOTS_AVAILABLE_KEY.formatted(matchId));
    }

    /**
     * SET NX로 원자적 선점
     * - 최초 호출 스레드만 true 반환
     * - 이후 호출은 이미 키가 존재하므로 false 반환
     */
    @Override
    public boolean acquirePassToken(UUID matchId, UUID userId) {
        String key = PASS_TOKEN_PREFIX + matchId + ":" + userId;

        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(key, PLACEHOLDER, TOKEN_TTL_MINUTES, TimeUnit.MINUTES); // SET NX EX

        return Boolean.TRUE.equals(success);
    }

    // 토큰 조회 (PENDING이면 발급 중이므로 null 처리)
    @Override
    public String getPassToken(UUID matchId, UUID userId) {
        String key = PASS_TOKEN_PREFIX + matchId + ":" + userId;
        String value = redisTemplate.opsForValue().get(key);
        return PLACEHOLDER.equals(value) ? null : value; // 발급 중이면 null 반환
    }

    // 선점 후 실제 토큰으로 덮어쓰기
    @Override
    public void savePassToken(UUID matchId, UUID userId, String token) {
        String key = PASS_TOKEN_PREFIX + matchId + ":" + userId;
        redisTemplate.opsForValue()
                .set(key, token, TOKEN_TTL_MINUTES, TimeUnit.MINUTES);
    }

    // 토큰 삭제
    @Override
    public void deletePassToken(UUID matchId, UUID userId) {
        redisTemplate.delete(PASS_TOKEN_PREFIX + matchId + ":" + userId);
    }


    private String getKey(UUID matchId) {
        return String.format(QUEUE_KEY, matchId);
    }

}
