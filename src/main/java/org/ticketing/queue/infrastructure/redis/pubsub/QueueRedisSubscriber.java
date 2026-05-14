package org.ticketing.queue.infrastructure.redis.pubsub;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.ticketing.queue.application.service.QueueHistoryService;
import org.ticketing.queue.domain.model.QueueExitReason;
import org.ticketing.queue.domain.model.QueueToken;
import org.ticketing.queue.domain.model.SlotAcquire;
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

    private final QueueHistoryService queueHistoryService;
    private final QueueRedisRepository queueRedisRepository;
    private final QueueTokenDomainService queueTokenDomainService;
    private final SseEmitterRepository sseEmitterRepository;

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
        LocalDateTime enteredAt = null;

        try {
            SlotAcquire acquireResult = queueRedisRepository.acquireSlotAndToken(matchId, userId);

            switch (acquireResult.status()) {
                case NO_SLOT -> {
                    // 슬롯 경합 패배 → 다음 스케줄러 주기에 재시도
                    return;
                }
                case PENDING -> {
                    // 다른 스레드가 발급 중 → 슬롯 획득 자체를 안 했으므로 반환 불필요
                    return;
                }
                case USER_NOT_IN_QUEUE -> {
                    // ban/refresh/rollback 등으로 이미 대기열에서 제거된 유저
                    // 슬롯 미획득이므로 rollback 불필요, emitter만 정리
                    log.warn("[SSE] 슬롯 획득 시점에 유저 없음(이미 퇴장). matchId={}, userId={}", matchId, userId);
                    emitter.complete();
                    return;
                }
                case ALREADY_ISSUED -> {
                    // 이미 발급 완료 → 슬롯 획득 안 했으므로 반환 불필요
                    String existingToken = queueRedisRepository.getPassToken(matchId, userId);
                    try {
                        sendEvent(emitter, UserStatusResponse.ofPassed(rank, totalCount, existingToken));
                    } catch (IOException e) {
                        log.warn("[SSE] ALREADY_ISSUED 전송 실패. matchId={}, userId={}", matchId, userId);
                    }
                    emitter.complete(); // onCompletion → remove()
                    return;
                }
                case SUCCESS -> {
                    slotAcquired = true;
                    // Lua 스크립트가 원자적으로 읽은 값 → 별도 getEnteredAt() 호출 불필요
                    enteredAt = acquireResult.enteredAt();
                }
            }

            // 토큰 발급 및 저장
            QueueToken token = queueTokenDomainService.issue(matchId, userId);
            queueRedisRepository.savePassToken(matchId, userId, token.getToken());
            queueRedisRepository.exit(matchId, userId);

            queueHistoryService.record(matchId, userId, enteredAt, QueueExitReason.PASSED);

            sendEvent(emitter, UserStatusResponse.ofPassed(rank, totalCount, token.getToken()));
            emitter.complete(); // onCompletion → remove()

        } catch (IOException e) {
            log.warn("[SSE] 전송 실패. matchId={}, userId={}", matchId, userId);
            rollback(matchId, userId, slotAcquired, true, enteredAt, QueueExitReason.IO_ERROR);
            emitter.completeWithError(e); // onError → remove()

        } catch (Exception e) {
            log.error("[SSE] 예상치 못한 오류. matchId={}, userId={}", matchId, userId, e);
            rollback(matchId, userId, slotAcquired, true, enteredAt, QueueExitReason.UNEXPECTED_ERROR);
            emitter.completeWithError(e); // onError → remove()
        }
    }

    private void sendEvent(SseEmitter emitter, UserStatusResponse response) throws IOException {
        try {
            emitter.send(
                    SseEmitter.event()
                            .name("queue-status")
                            .data(response)
                            .id(String.valueOf(System.currentTimeMillis()))
                            .reconnectTime(3000)
            );
        } catch (IllegalStateException e) {
            // 이미 완료된 emitter → IOException으로 변환해 호출부에서 rollback 처리
            throw new IOException("Emitter already completed", e);
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
            enteredAt = LocalDateTime.now();
        }
        queueHistoryService.record(matchId, userId, enteredAt, reason);
    }
}