package org.ticketing.queue.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import org.ticketing.queue.domain.exception.NotFoundUserException;
import org.ticketing.queue.domain.exception.SlotException;
import org.ticketing.queue.domain.exception.TokenException;
import org.ticketing.queue.domain.model.AcquireResult;
import org.ticketing.queue.domain.model.Queue;
import org.ticketing.queue.domain.repository.QueueRedisRepository;
import org.ticketing.queue.domain.repository.QueueRepository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.ticketing.queue.infrastructure.util.RuaScript.ACQUIRE_SLOT_AND_TOKEN_SCRIPT;
import static org.ticketing.queue.infrastructure.util.RuaScript.RELEASE_SLOT_SCRIPT;

@Repository
@Slf4j
@RequiredArgsConstructor
public class QueueRedisRepositoryImpl implements QueueRedisRepository {

    private final RedisTemplate<String, String> redisTemplate;
    private final QueueRepository queueRepository;

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

    // 유저 대기열 진입 시간 조회
    @Override
    public LocalDateTime getEnteredAt(UUID matchId, UUID userId) {
        String key = getKey(matchId);

        Double score = redisTemplate.opsForZSet().score(key, userId.toString());

        // 대기열에 존재하지 않는 유저
        if (score == null) {
            throw new NotFoundUserException(matchId, userId);
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

    // 최대 슬롯, 사용 가능한 슬롯 초기 설정
    @Override
    public void initSlots(UUID matchId) {
        Queue queue = queueRepository.findByMatchId(matchId);
        String maxKey = String.format(SLOTS_MAX_KEY, matchId);
        String availableKey = String.format(SLOTS_AVAILABLE_KEY, matchId);

        queue.ready();

        redisTemplate.opsForValue().set(maxKey, String.valueOf(queue.getMaxActiveUsers()));
        redisTemplate.opsForValue().set(availableKey, String.valueOf(queue.getMaxActiveUsers()));
    }

    // 사용가능한 슬롯 수 확인
    @Override
    public Long getAvailableSlots(UUID matchId) {
        String value = redisTemplate.opsForValue().get(SLOTS_AVAILABLE_KEY.formatted(matchId));
        return value != null ? Long.parseLong(value) : 0L;
    }

    // 슬롯 반환
    @Override
    public void releaseSlot(UUID matchId) {
        String availableKey = SLOTS_AVAILABLE_KEY.formatted(matchId);
        String maxKey = SLOTS_MAX_KEY.formatted(matchId);

        Long result = redisTemplate.execute(RELEASE_SLOT_SCRIPT, List.of(availableKey, maxKey));

        if (result == null) {
            throw new SlotException("슬롯 반환 결과가 null입니다.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        if (result == -2) {
            throw new SlotException("슬롯 정보가 초기화되지 않았습니다.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        if (result == -1) {
            log.warn("[QUEUE] 슬롯 최대치 초과 반환 방지 matchId={}", matchId);
            return;
        }
        log.info("[queue-service] 대기열 슬롯 반환: {}", matchId);
    }

    /**
     * 결과에 따라 분기만 처리
     * 원자적으로 슬롯+토큰 동시 획득
     */
    @Override
    public AcquireResult acquireSlotAndToken(UUID matchId, UUID userId) {
        String tokenKey = PASS_TOKEN_PREFIX + matchId + ":" + userId;
        String availableKey = SLOTS_AVAILABLE_KEY.formatted(matchId);
        long ttlSeconds = TOKEN_TTL_MINUTES * 60;

        Long result = redisTemplate.execute(
                ACQUIRE_SLOT_AND_TOKEN_SCRIPT,
                List.of(tokenKey, availableKey),
                String.valueOf(ttlSeconds),
                PLACEHOLDER
        );

        if (result == null) {
            throw new SlotException("슬롯+토큰 선점 결과가 null입니다.",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return switch (result.intValue()) {
            case  1  -> AcquireResult.SUCCESS;
            case -1  -> AcquireResult.NO_SLOT;
            case -2  -> throw new SlotException("슬롯 미초기화",
                    HttpStatus.INTERNAL_SERVER_ERROR);
            case -3  -> AcquireResult.PENDING;
            case -4  -> AcquireResult.ALREADY_ISSUED;
            default  -> throw new SlotException("알 수 없는 결과: " + result,
                    HttpStatus.INTERNAL_SERVER_ERROR);
        };
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
            throw new TokenException(String.format("해당 토큰이 존재하지 않습니다. matchId = %s, userId = %s", matchId, userId));
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
