package org.ticketing.queue.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import org.ticketing.queue.domain.exception.*;
import org.ticketing.queue.domain.model.AcquireResult;
import org.ticketing.queue.domain.model.Queue;
import org.ticketing.queue.domain.model.SlotAcquire;
import org.ticketing.queue.domain.repository.QueueRedisRepository;
import org.ticketing.queue.domain.repository.QueueRepository;

import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.ticketing.queue.infrastructure.util.RuaScript.*;

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
    private static final String PASS_TOKEN_KEY = "queue:pass-token:%s:%s"; // queue:pass-token:{matchId}:{userId}
    private static final String ENTERED_AT_KEY = "queue:entered-at:%s"; // queue:entered-at:{matchId}
    private static final String BANNED_USER_KEY = "queue:banned:%s:%s"; // queue:banned:{matchId}:{userId}
    private static final String OPEN_AT_KEY = "queue:open-at:%s"; // queue:open-at:{matchId}

    private static final String PLACEHOLDER = "PENDING";   // 토큰 발급 전 선점 표시
    private static final long TOKEN_TTL_MINUTES = 10L;


    // 유저 대기열 진입 (QUEUE_SEQUENCE_KEY 순번 증가)
    @Override
    public boolean entry(UUID matchId, UUID userId) {
        validateTicketOpenAt(matchId); // 예매 시작 시간 체크

        String queueKey = getKey(matchId);
        String sequenceKey = getSeqKey(matchId);
        String bannedKey = getBannedKey(matchId, userId);
        String enteredAtKey = getEnteredAtKey(matchId);

        /**
         * 차단된 유저 진입 차단
         * 진입 성공 시 입장 시각 저장
         * 대기 순번 증가
         */
        Long result = redisTemplate.execute(
                ENTRY_SCRIPT,
                List.of(queueKey, sequenceKey, bannedKey, enteredAtKey),
                userId.toString(),
                String.valueOf(System.currentTimeMillis())
        );

        if (Long.valueOf(-1L).equals(result)) {
            throw new BannedUserException(matchId, userId);
        }
        if (Long.valueOf(0L).equals(result)) {
            throw new AlreadyWaitingQueueException(matchId, userId);
        }

        return Long.valueOf(1L).equals(result);
    }

    // 대기열 진입 전 예매 시작 시간 체크
    private void validateTicketOpenAt(UUID matchId) {
        String openAtKey = String.format(OPEN_AT_KEY, matchId);
        String openAtValue = redisTemplate.opsForValue().get(openAtKey);

        // null이면 키가 만료된 것 = 이미 오픈 시간이 지남 → 통과
        if (openAtValue == null) {
            return;
        }

        long openAtEpoch = Long.parseLong(openAtValue);
        long nowEpoch = OffsetDateTime.now(ZoneOffset.UTC).toEpochSecond();

        if (nowEpoch < openAtEpoch) {
            throw new QueueNotOpenException(matchId);
        }
    }

    // 유저의 현재 순번 조회 (0부터 시작하므로 +1)
    @Override
    public Long getRank(UUID matchId, UUID userId) {
        String key = getKey(matchId);
        Long rank = redisTemplate.opsForZSet().rank(key, userId.toString());
        return rank != null ? rank + 1 : null;
    }

    // 유저의 순번 조회(배치)
    @Override
    public Map<UUID, Long> getRankBatch(UUID matchId, List<UUID> userIds) {
        String key = getKey(matchId);
        List<Object> results = redisTemplate.executePipelined((RedisCallback<Object>) conn -> {
            for (UUID userId : userIds) {
                conn.zSetCommands().zRank(key.getBytes(), userId.toString().getBytes());
            }
            return null;
        });

        Map<UUID, Long> rankMap = new HashMap<>();
        for (int i = 0; i < userIds.size(); i++) {
            Long rank = (Long) results.get(i);
            rankMap.put(userIds.get(i), rank != null ? rank + 1 : null);
        }
        return rankMap;
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
        String enteredAtKey = getEnteredAtKey(matchId);
        String value = (String) redisTemplate.opsForHash().get(enteredAtKey, userId.toString());

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
        String enteredAtKey = getEnteredAtKey(matchId);
        redisTemplate.opsForHash().delete(enteredAtKey, userId.toString());
    }

    // 최대 슬롯, 사용 가능한 슬롯 초기 설정
    @Override
    public void initSlots(UUID matchId, OffsetDateTime ticketOpenAt) {
        Queue queue = queueRepository.findByMatchId(matchId);
        String maxKey = String.format(SLOTS_MAX_KEY, matchId);
        String availableKey = String.format(SLOTS_AVAILABLE_KEY, matchId);
        String openAtKey = String.format(OPEN_AT_KEY, matchId);

        // 예매 시작 시간 저장 + 자동 만료 설정
        long nowEpoch = OffsetDateTime.now(ZoneOffset.UTC).toEpochSecond();
        long openAtEpoch = ticketOpenAt.toEpochSecond();
        long ttlSeconds = openAtEpoch - nowEpoch;

        // 대기열 READY 상태로 변경
        queue.ready();

        redisTemplate.opsForValue().set(maxKey, String.valueOf(queue.getMaxActiveUsers()));
        redisTemplate.opsForValue().set(availableKey, String.valueOf(queue.getMaxActiveUsers()));
        // 예매 시작 시간 저장 (epoch second로 저장 - 비교 연산 용이)
        if (ttlSeconds > 0) {
            redisTemplate.opsForValue().set(openAtKey, String.valueOf(openAtEpoch), ttlSeconds, TimeUnit.SECONDS);
        } else {
            // 이미 오픈 시간이 지난 경우 (혹은 즉시 오픈)
            redisTemplate.opsForValue().set(openAtKey, String.valueOf(openAtEpoch));
        }
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
     * 원자적으로 슬롯+토큰 선점 및 enteredAt 조회
     * Lua 스크립트가 enteredAt 존재 여부를 슬롯 획득 전에 체크하므로
     * acquireSlot SUCCESS 이후 별도 getEnteredAt() 호출 불필요
     */
    @Override
    @SuppressWarnings("unchecked")
    public SlotAcquire acquireSlotAndToken(UUID matchId, UUID userId) {
        String tokenKey = getPassTokenKey(matchId, userId);
        String availableKey = SLOTS_AVAILABLE_KEY.formatted(matchId);
        String enteredAtKey = getEnteredAtKey(matchId);
        long ttlSeconds = TOKEN_TTL_MINUTES * 60;

        List<Object> result = (List<Object>) redisTemplate.execute(
                ACQUIRE_SLOT_AND_TOKEN_SCRIPT,
                List.of(tokenKey, availableKey, enteredAtKey),
                userId.toString(),
                String.valueOf(ttlSeconds),
                PLACEHOLDER
        );

        if (result == null || result.isEmpty()) {
            throw new SlotException("슬롯+토큰 선점 결과가 null입니다.", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        long code = (Long) result.get(0);

        return switch ((int) code) {
            case  1 -> {
                String raw = (String) result.get(1);
                LocalDateTime enteredAt = Instant.ofEpochMilli(Long.parseLong(raw))
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime();
                yield SlotAcquire.success(enteredAt);
            }
            case -1 -> SlotAcquire.of(AcquireResult.NO_SLOT);
            case -2 -> throw new SlotException("슬롯 미초기화", HttpStatus.INTERNAL_SERVER_ERROR);
            case -3 -> SlotAcquire.of(AcquireResult.PENDING);
            case -4 -> SlotAcquire.of(AcquireResult.ALREADY_ISSUED);
            case -5 -> SlotAcquire.of(AcquireResult.USER_NOT_IN_QUEUE);
            default -> throw new SlotException("알 수 없는 결과: " + code, HttpStatus.INTERNAL_SERVER_ERROR);
        };
    }

    // 토큰 조회 (PENDING이면 발급 중이므로 null 처리)
    @Override
    public String getPassToken(UUID matchId, UUID userId) {
        String key = getPassTokenKey(matchId, userId);
        String value = redisTemplate.opsForValue().get(key);
        return PLACEHOLDER.equals(value) ? null : value; // 발급 중이면 null 반환
    }

    // 선점 후 실제 토큰으로 덮어쓰기
    @Override
    public void savePassToken(UUID matchId, UUID userId, String token) {
        String key = getPassTokenKey(matchId, userId);
        redisTemplate.opsForValue().set(key, token, TOKEN_TTL_MINUTES, TimeUnit.MINUTES);
    }

    // 토큰 삭제
    @Override
    public void deletePassToken(UUID matchId, UUID userId) {
        String key = getPassTokenKey(matchId, userId);
        redisTemplate.delete(key);
    }

    @Override
    public String findPassToken(UUID matchId, UUID userId) {
        String key = getPassTokenKey(matchId, userId);
        String token = redisTemplate.opsForValue().get(key);

        if (token == null) {
            throw new TokenException(String.format("해당 토큰이 존재하지 않습니다. matchId = %s, userId = %s", matchId, userId));
        }
        return token;
    }

    @Override
    public LocalDateTime getExpiredAt(UUID matchId, UUID userId) {
        String key = getPassTokenKey(matchId, userId);
        Long ttlSeconds = redisTemplate.getExpire(key, TimeUnit.SECONDS);

        if (ttlSeconds == null || ttlSeconds < 0) {
            throw new TokenException(String.format("토큰이 존재하지 않거나 만료 시간이 없습니다. matchId = %s, userId = %s", matchId, userId));
        }

        return LocalDateTime.now().plusSeconds(ttlSeconds);
    }

    /**
     * 해당 matchId의 모든 Redis 데이터 초기화
     * - queue:{matchId}                     → 대기열 sortedSet
     * - queue:slots:max:{matchId}           → 최대 슬롯
     * - queue:slots:available:{matchId}     → 남은 슬롯
     * - queue:seq:{seq}                     → seq 키
     * - queue:open-at:{matchId}             → 예매 오픝 시간
     * - queue:entered-at:{matchId}          → 유저별 대기열 입장 시간
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

        // 4. open-at 키 삭제
        redisTemplate.delete(OPEN_AT_KEY.formatted(matchId));

        // 5. 해당 matchId의 통과 토큰 전체 삭제 (SCAN 사용 - keys() 운영 부하 방지)
        deletePassTokensByMatchId(matchId);
        // 6. enteredAt 키 삭제 추가
        deleteEnteredAtByMatchId(matchId);

        log.info("[Redis] 대기열 초기화 완료. matchId={}", matchId);
    }

    /**
     * SCAN으로 pass-token:{matchId}:* 패턴 키 순회 삭제
     * keys() 대신 scan() 사용 → 운영 환경 블로킹 방지
     */
    private void deletePassTokensByMatchId(UUID matchId) {
        String pattern = String.format("queue:pass-token:%s:*", matchId);

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

    private void deleteEnteredAtByMatchId(UUID matchId) {
        String key = getEnteredAtKey(matchId);
        redisTemplate.delete(key);
        log.info("[Redis] entered-at 삭제 완료. matchId={}", matchId);
    }

    @Override
    public void saveBannedUser(UUID matchId, UUID userId) {
        redisTemplate.opsForValue().set(getBannedKey(matchId, userId), "1");
    }

    private String getKey(UUID matchId) {
        return String.format(QUEUE_KEY, matchId);
    }

    private String getSeqKey(UUID matchId) {
        return String.format(QUEUE_SEQUENCE_KEY, matchId);
    }

    private String getEnteredAtKey(UUID matchId) {
        return String.format(ENTERED_AT_KEY, matchId);
    }

    private String getPassTokenKey(UUID matchId, UUID userId) {
        return String.format(PASS_TOKEN_KEY, matchId, userId);
    }

    private String getBannedKey(UUID matchId, UUID userId) {
        return String.format(BANNED_USER_KEY, matchId, userId);
    }
}
