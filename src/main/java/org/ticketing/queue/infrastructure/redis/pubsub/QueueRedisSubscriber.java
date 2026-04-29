package org.ticketing.queue.infrastructure.redis.pubsub;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.ticketing.queue.domain.model.QueueExitReason;
import org.ticketing.queue.domain.model.QueueHistory;
import org.ticketing.queue.domain.model.QueueToken;
import org.ticketing.queue.domain.repository.QueueHistoryRepository;
import org.ticketing.queue.domain.repository.QueueRedisRepository;
import org.ticketing.queue.domain.service.QueueTokenDomainService;
import org.ticketing.queue.infrastructure.persistence.SseEmitterRepository;
import org.ticketing.queue.presentation.dto.response.UserStatusResponse;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueueRedisSubscriber implements MessageListener {

    private final QueueRedisRepository queueRedisRepository;
    private final QueueTokenDomainService queueTokenDomainService;
    private final QueueHistoryRepository queueHistoryRepository;
    private final SseEmitterRepository sseEmitterRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        UUID matchId;
        try {
            matchId = UUID.fromString(new String(message.getBody()));
        } catch (Exception e) {
            log.error("[Pub/Sub] matchId 파싱 실패: {}", new String(message.getBody()), e);
            return;
        }

        List<UUID> userIds = sseEmitterRepository.findUserIdsByMatchId(matchId);
        if (userIds.isEmpty()) {
            log.debug("[Pub/Sub] 이 인스턴스에 연결된 유저 없음. matchId={}", matchId);
            return;
        }

        Long totalCount = queueRedisRepository.getTotalCount(matchId);
        Long availableSlots = queueRedisRepository.getAvailableSlots(matchId);

        for (UUID userId : userIds) {
            SseEmitter emitter = sseEmitterRepository.find(matchId, userId);
            if (emitter == null) continue;

            Long rank = queueRedisRepository.getRank(matchId, userId);

            // 슬롯 범위 밖 유저는 스케줄러가 처리하므로 스킵
            if (rank == null || rank > availableSlots) continue;

            pushStatus(matchId, userId, emitter, rank, totalCount);
        }
    }

    public void pushStatus(UUID matchId, UUID userId, SseEmitter emitter, Long rank, Long totalCount) {
        boolean slotAcquired = false;
        boolean tokenAcquired = false;
        LocalDateTime enteredAt = null;

        try {
            slotAcquired = queueRedisRepository.acquireSlot(matchId);
            if (!slotAcquired) {
                // 슬롯 경합 패배 → 스케줄러가 다음 주기에 순위 업데이트
                return;
            }

            tokenAcquired = queueRedisRepository.acquirePassToken(matchId, userId);

            // 유저별 중복 발급 방지
            if (!tokenAcquired) {
                String existingToken = queueRedisRepository.getPassToken(matchId, userId);
                // 토큰 없으면 통과
                if (existingToken == null) return;

                // 토큰 존재하면 토큰 반환
                sendEvent(emitter, UserStatusResponse.ofPassed(rank, totalCount, existingToken));
                sseEmitterRepository.remove(matchId, userId);
                emitter.complete();
                return;
            }

            // 토큰 발급 후 저장
            QueueToken token = queueTokenDomainService.issue(matchId, userId);
            enteredAt = queueRedisRepository.getEnteredAt(matchId, userId);
            queueRedisRepository.savePassToken(matchId, userId, token.getToken());
            queueRedisRepository.exit(matchId, userId);

            // 대기열 이탈 로그 기록
            recordHistory(matchId, userId, enteredAt, QueueExitReason.PASSED);

            // SSE 전송
            sendEvent(emitter, UserStatusResponse.ofPassed(rank, totalCount, token.getToken()));
            sseEmitterRepository.remove(matchId, userId);
            emitter.complete();

        } catch (IOException e) {
            log.warn("[SSE] 전송 실패. matchId={}, userId={}", matchId, userId);
            rollback(matchId, userId, slotAcquired, tokenAcquired, enteredAt, QueueExitReason.IO_ERROR);    // 토큰, 슬롯 획득했을 시 제거 및 반환
            sseEmitterRepository.remove(matchId, userId);
            emitter.completeWithError(e);

        } catch (Exception e) {
            log.error("[SSE] 예상치 못한 오류. matchId={}, userId={}", matchId, userId, e);
            rollback(matchId, userId, slotAcquired, tokenAcquired, enteredAt, QueueExitReason.UNEXPECTED_ERROR);
            sseEmitterRepository.remove(matchId, userId);
            emitter.completeWithError(e);
        }
    }

    private void sendEvent(SseEmitter emitter, UserStatusResponse response) throws IOException {
        try {
            emitter.send(
                    SseEmitter.event()
                            .name("queue-status")
                            .data(objectMapper.writeValueAsString(response))
                            .id(String.valueOf(System.currentTimeMillis()))
                            .reconnectTime(3000)
            );
        } catch (IllegalStateException e) {
            log.warn("[SSE] 이미 완료된 emitter. 전송 스킵");
        }
    }

    private void rollback(UUID matchId, UUID userId, boolean slotAcquired, boolean tokenAcquired, LocalDateTime enteredAt, QueueExitReason reason) {
        if (tokenAcquired) {
            queueRedisRepository.deletePassToken(matchId, userId);
        }
        if (slotAcquired) {
            queueRedisRepository.releaseSlot(matchId);
        }
        if (enteredAt == null) {
            enteredAt = queueRedisRepository.getEnteredAt(matchId, userId);
        }
        recordHistory(matchId, userId, enteredAt, reason);
    }

    public void recordHistory(UUID matchId, UUID userId, LocalDateTime enteredAt, QueueExitReason exitReason) {
        QueueHistory history = switch (exitReason) {
            case PASSED -> QueueHistory.ofPassed(matchId, userId, enteredAt);
            case IO_ERROR -> QueueHistory.ofIoError(matchId, userId, enteredAt);
            case UNEXPECTED_ERROR -> QueueHistory.ofUnexpectedError(matchId, userId, enteredAt);
            case TIMEOUT -> QueueHistory.ofTimeout(matchId, userId, enteredAt);
        };

        try {
            queueHistoryRepository.save(history);
            log.debug("[QueueHistory] 저장 완료. matchId={}, userId={}, reason={}",
                    history.getMatchId(), history.getUserId(), history.getExitReason());
        } catch (Exception e) {
            log.warn("[QueueHistory] 저장 실패. matchId={}, userId={}, reason={}",
                    history.getMatchId(), history.getUserId(), history.getExitReason(), e);
        }
    }
}