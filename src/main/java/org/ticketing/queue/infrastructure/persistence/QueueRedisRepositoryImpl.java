package org.ticketing.queue.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import org.ticketing.queue.domain.exception.QueueException;
import org.ticketing.queue.domain.repository.QueueRedisRepository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.ticketing.queue.infrastructure.util.RuaScript.ACQUIRE_SLOT_SCRIPT;
import static org.ticketing.queue.infrastructure.util.RuaScript.RELEASE_SLOT_SCRIPT;

@Repository
@Slf4j
@RequiredArgsConstructor
public class QueueRedisRepositoryImpl implements QueueRedisRepository {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String QUEUE_KEY = "queue:%s"; // queue:{matchId}
    private static final String SLOTS_MAX_KEY  = "queue:slots:max:%s";  // 최대 슬롯
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
        String key = SLOTS_AVAILABLE_KEY.formatted(matchId);
        Long result = redisTemplate.execute(ACQUIRE_SLOT_SCRIPT, Collections.singletonList(key));

        if (result == null) {
            throw new QueueException("Redis 슬롯 선점 결과가 null입니다.", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return result >= 0;
    }

    // 슬롯 반환
    @Override
    public void releaseSlot(UUID matchId) {
        String availableKey = SLOTS_AVAILABLE_KEY.formatted(matchId);
        String maxKey = SLOTS_MAX_KEY.formatted(matchId);

        Long result = redisTemplate.execute(RELEASE_SLOT_SCRIPT, List.of(availableKey, maxKey));

        if (result == null) {
            throw new QueueException("슬롯 반환 결과가 null입니다.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        if (result == -2) {
            throw new QueueException("슬롯 정보가 초기화되지 않았습니다.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        if (result == -1) {
            log.warn("[QUEUE] 슬롯 최대치 초과 반환 방지 matchId={}", matchId);
        }
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

    @Override
    public String findPassToken(UUID matchId, UUID userId) {
        String key = PASS_TOKEN_PREFIX + matchId + ":" + userId;
        String token = redisTemplate.opsForValue().get(key);

        if (token == null) {
            throw new QueueException("해당 토큰이 존재하지 않습니다.", HttpStatus.NOT_FOUND);
        }
        return token;
    }

    @Override
    public LocalDateTime getExpiredAt(UUID matchId, UUID userId) {
        String key = PASS_TOKEN_PREFIX + matchId + ":" + userId;
        Long ttlSeconds = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return LocalDateTime.now().plusSeconds(ttlSeconds);
    }

    private String getKey(UUID matchId) {
        return String.format(QUEUE_KEY, matchId);
    }

}
