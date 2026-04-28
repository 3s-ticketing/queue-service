package org.ticketing.queue.infrastructure.redis.stream;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.ticketing.queue.infrastructure.redis.pubsub.QueueRedisPublisher;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueueStreamConsumer {

    private final StringRedisTemplate redisTemplate;
    private final QueueRedisPublisher queueRedisPublisher;

    private static final String STREAM_KEY = "queue-events";
    private static final String GROUP_NAME = "queue-group";
    private static final String CONSUMER_NAME = "queue-consumer-1";

    @Scheduled(fixedDelay = 100)
    public void consume() {
        List<MapRecord<String, Object, Object>> records;

        try {
            records = redisTemplate.opsForStream()
                    .read(Consumer.from(GROUP_NAME, CONSUMER_NAME),
                            StreamReadOptions.empty().count(30),
                            StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed()));

        } catch (RedisSystemException e) {
            // 스트림/그룹 없을 때 → 로그만 찍고 다음 주기에 재시도
            // ERROR 대신 WARN으로 낮춤 (초기화 타이밍 이슈)
            log.warn("[Stream] XREAD 실패 (스트림/그룹 미존재 가능성): {}", e.getMessage());
            return;
        } catch (Exception e) {
            log.error("[Stream] XREAD 예외", e);
            return;
        }

        if (records == null || records.isEmpty()) return;

        for (MapRecord<String, Object, Object> record : records) {
            try {
                String matchIdStr = (String) record.getValue().get("matchId");
                String type = (String) record.getValue().get("type");

                switch (type) {
                    case "SLOT_RELEASED" -> {
                        UUID matchId = UUID.fromString(matchIdStr);
                        log.debug("[Stream] 슬롯 반환 이벤트 수신. matchId={}, recordId={}", matchId, record.getId());
                        // 모든 인스턴스의 Subscriber에게 브로드캐스트
                        queueRedisPublisher.publish(matchId);
                        // 정상 처리 후 ACK
                        redisTemplate.opsForStream().acknowledge(STREAM_KEY, GROUP_NAME, record.getId());
                    }
                    default -> {
                        // 알 수 없는 타입은 ACK 후 스킵
                        log.warn("[Stream] 알 수 없는 타입: {}", type);
                        redisTemplate.opsForStream().acknowledge(STREAM_KEY, GROUP_NAME, record.getId());
                    }
                }

            } catch (Exception e) {
                // ACK 하지 않음 → PEL에 남아 재처리 가능
                log.error("[Stream] 이벤트 처리 실패. recordId={}", record.getId(), e);
            }
        }
    }
}