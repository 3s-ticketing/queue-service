package org.ticketing.queue.infrastructure.messaging.kafka.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.ticketing.common.messaging.annotation.IdempotentConsumer;
import org.ticketing.queue.domain.event.MatchApprovedEvent;
import org.ticketing.queue.domain.repository.QueueRedisRepository;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class MatchEventConsumer {

    private final QueueRedisRepository queueRedisRepository;
    private final ObjectMapper objectMapper;

    // 예매 완료/취소 - 슬롯 1개 증가
    @KafkaListener(topics = "match.approved", groupId = "queue-service")
    @IdempotentConsumer("queue-service")
    public void handleMatchEvent(ConsumerRecord<String, String> record, Acknowledgment ack) throws JsonProcessingException {
        MatchApprovedEvent event = null;
        try {
            Header traceHeader = record.headers().lastHeader("traceId");
            String traceId = traceHeader != null
                    ? new String(traceHeader.value(), StandardCharsets.UTF_8)
                    : "consume-" + UUID.randomUUID().toString().substring(0, 8);
            MDC.put("traceId", traceId);

            event = objectMapper.readValue(record.value(), MatchApprovedEvent.class);
            queueRedisRepository.initSlots(event.matchId());
            log.info("[queue-service] 레디스 대기열 설정 추가: {}", event.matchId());
            ack.acknowledge(); // 정상 처리 후 ACK

        } catch (Exception e) {
            log.error("[queue-service] 슬롯 반환 실패: {}", event.matchId(), e);
            // ACK 안 하면 → DefaultErrorHandler → DLT로 이동
            throw e;

        } finally {
            MDC.clear();
        }
    }

    // DLT 이벤트 수신
    @KafkaListener(topics = "match.approved.DLT", groupId = "queue-service")
    @IdempotentConsumer("queue-service")
    public void handleMatchDltEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        MatchApprovedEvent event = null;
        try {
            Header traceHeader = record.headers().lastHeader("traceId");
            String traceId = traceHeader != null
                    ? new String(traceHeader.value(), StandardCharsets.UTF_8)
                    : "consume-" + UUID.randomUUID().toString().substring(0, 8);
            MDC.put("traceId", traceId);

            event = objectMapper.readValue(record.value(), MatchApprovedEvent.class);
            log.info("[queue-service] dlt 수신, 레디스 대기열 설정 추가 수동 처리 필요: {}", event.matchId());

        } catch (Exception e) {
            log.error("[queue-service] dlt 수신, 에러 발생: {}", event.matchId(), e);

        } finally {
            MDC.clear();
            ack.acknowledge();
        }
    }
}
