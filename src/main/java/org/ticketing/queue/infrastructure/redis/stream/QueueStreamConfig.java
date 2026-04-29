package org.ticketing.queue.infrastructure.redis.stream;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class QueueStreamConfig {

    private final StringRedisTemplate redisTemplate;
    private static final String STREAM_KEY = "queue-events";
    private static final String GROUP_NAME = "queue-group";

    @PostConstruct
    public void initConsumerGroup() {
        try {
            // 스트림 키가 없으면 더미 메시지로 먼저 생성 후 즉시 삭제
            Boolean exists = redisTemplate.hasKey(STREAM_KEY);
            if (Boolean.FALSE.equals(exists)) {
                // XADD로 스트림 키 생성
                RecordId dummyId = redisTemplate.opsForStream()
                        .add(STREAM_KEY, Map.of("init", "true"));
                // 더미 메시지 즉시 삭제
                redisTemplate.opsForStream().delete(STREAM_KEY, dummyId);
                log.info("[Stream] 스트림 키 생성 완료: {}", STREAM_KEY);
            }

            // Consumer Group 생성
            redisTemplate.opsForStream()
                    .createGroup(STREAM_KEY, ReadOffset.latest(), GROUP_NAME);
            log.info("[Stream] Consumer Group 생성 완료: {}", GROUP_NAME);

        } catch (RedisSystemException e) {
            String message = e.getMessage() != null ? e.getMessage() : "";

            if (message.contains("BUSYGROUP")) {
                // 이미 존재 → 정상, 앱 계속 실행
                log.debug("[Stream] Consumer Group 이미 존재 (정상): {}", GROUP_NAME);
            } else if (message.contains("ERR The OBJECT subcommand")) {
                // 스트림 키 관련 기타 Redis 예외 → 무시
                log.warn("[Stream] 스트림 초기화 경고 (무시): {}", message);
            } else {
                // 진짜 알 수 없는 예외 → 로그만 찍고 앱은 띄움
                log.error("[Stream] Consumer Group 초기화 실패 (앱은 계속 실행): {}", message);
            }
        } catch (Exception e) {
            log.error("[Stream] Consumer Group 초기화 중 예외 발생 (앱은 계속 실행): {}", e.getMessage());
        }
    }
}