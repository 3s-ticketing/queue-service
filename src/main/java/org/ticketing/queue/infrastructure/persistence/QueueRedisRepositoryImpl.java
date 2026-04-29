package org.ticketing.queue.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
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
import java.util.ArrayList;
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
    private static final String QUEUE_SEQUENCE_KEY = "queue:seq:%s";  // 대기 순번
    private static final String SLOTS_MAX_KEY  = "queue:slots:max:%s";  // 최대 슬롯
    private static final String SLOTS_AVAILABLE_KEY  = "queue:slots:available:%s";  // 남은 슬롯
    private static final String PASS_TOKEN_PREFIX = "queue:pass-token:";
    private static final String ENTERED_AT_KEY = "queue:entered-at:%s:%s"; // queue:entered-at:{matchId}:{userId}
    private static final String PLACEHOLDER = "PENDING";   // 토큰 발급 전 선점 표시
    private static final long TOKEN_TTL_MINUTES = 10L;

    // 유저 대기열 진입 (score = 진입 시각)
    @Override
    public boolean entry(UUID matchId, UUID userId) {
        String key = getSeqKey(matchId);

        Long sequence = redisTemplate.opsForValue().increment(key);

        boolean added = Boolean.TRUE.equals(
                redisTemplate.opsForZSet().addIfAbsent(
                        key,
                        userId.toString(),
                        sequence.doubleValue()
                )
        );

        // 진입 성공 시 입장 시각 별도 저장
        if (added) {
            String enteredAtKey = getEnteredAtKey(matchId, userId);
            redisTemplate.opsForValue().set(
                    enteredAtKey,
                    String.valueOf(System.currentTimeMillis())
            );
        }

        return added;
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
        // sortedSet score 대신 별도 키에서 조회
        String enteredAtKey = getEnteredAtKey(matchId, userId);
        String value = redisTemplate.opsForValue().get(enteredAtKey);

        // 대기열에 존재하지 않는 유저
        if (value == null) {
            throw new NotFoundUserException(matchId, userId);
        }

        return Instant.ofEpochMilli(Long.parseLong(value))
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
    }

    // 유저 대기열 이탈
    @Override
    public void exit(UUID matchId, UUID userId) {
        // 대기열에서 삭제
        String key = getKey(matchId);
        redisTemplate.opsForZSet().remove(key, userId.toString());

        // enteredAt 키 삭제
        String enteredAtKey = getEnteredAtKey(matchId, userId);
        redisTemplate.delete(enteredAtKey);
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

    /**
     * 해당 matchId의 모든 Redis 데이터 초기화
     * - queue:{matchId}                     → 대기열 sortedSet
     * - queue:slots:max:{matchId}           → 최대 슬롯
     * - queue:slots:available:{matchId}     → 남은 슬롯
     * - queue:pass-token:{matchId}:*        → 통과 토큰 전체
     */
    @Override
    public void refreshQueue(UUID matchId) {
        // 1. 대기열 sortedSet 삭제
        redisTemplate.delete(getKey(matchId));

        // 2. 슬롯 삭제 (max, available)
        redisTemplate.delete(SLOTS_MAX_KEY.formatted(matchId));
        redisTemplate.delete(SLOTS_AVAILABLE_KEY.formatted(matchId));

        // 3. seq 키 삭제(대기 순번)
        redisTemplate.delete(getSeqKey(matchId));

        // 4. 해당 matchId의 통과 토큰 전체 삭제 (SCAN 사용 - keys() 운영 부하 방지)
        deletePassTokensByMatchId(matchId);
        // 5. enteredAt 키 삭제 추가
        deleteEnteredAtByMatchId(matchId);

        log.info("[Redis] 대기열 초기화 완료. matchId={}", matchId);
    }

    private void deleteEnteredAtByMatchId(UUID matchId) {
        String pattern = String.format("queue:entered-at:%s:*", matchId);

        ScanOptions options = ScanOptions.scanOptions()
                .match(pattern)
                .count(100)
                .build();

        List<String> keysToDelete = new ArrayList<>();
        try (Cursor<byte[]> cursor = redisTemplate.getConnectionFactory()
                .getConnection()
                .scan(options)) {
            while (cursor.hasNext()) {
                keysToDelete.add(new String(cursor.next()));
            }
        } catch (Exception e) {
            log.error("[Redis] entered-at SCAN 실패. matchId={}", matchId, e);
            throw new SlotException("대기열 초기화 중 entered-at 삭제 실패", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        if (!keysToDelete.isEmpty()) {
            redisTemplate.delete(keysToDelete);
        }
    }

    /**
     * SCAN으로 pass-token:{matchId}:* 패턴 키 순회 삭제
     * keys() 대신 scan() 사용 → 운영 환경 블로킹 방지
     */
    private void deletePassTokensByMatchId(UUID matchId) {
        String pattern = PASS_TOKEN_PREFIX + matchId + ":*";

        ScanOptions options = ScanOptions.scanOptions()
                .match(pattern)
                .count(100)     // 한 번에 스캔할 커서 힌트
                .build();

        List<String> keysToDelete = new ArrayList<>();

        try (Cursor<byte[]> cursor = redisTemplate.getConnectionFactory()
                .getConnection()
                .scan(options)) {

            while (cursor.hasNext()) {
                keysToDelete.add(new String(cursor.next()));
            }

        } catch (Exception e) {
            log.error("[Redis] pass-token SCAN 실패. matchId={}", matchId, e);
            throw new SlotException("대기열 초기화 중 토큰 삭제 실패", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        if (!keysToDelete.isEmpty()) {
            redisTemplate.delete(keysToDelete);
            log.info("[Redis] 토큰 {}건 삭제 완료. matchId={}", keysToDelete.size(), matchId);
        }
    }

    private String getKey(UUID matchId) {
        return String.format(QUEUE_KEY, matchId);
    }

    private String getSeqKey(UUID matchId) {
        return String.format(QUEUE_SEQUENCE_KEY, matchId);
    }

    private String getEnteredAtKey(UUID matchId, UUID userId) {
        return String.format(ENTERED_AT_KEY, matchId, userId);
    }
}
