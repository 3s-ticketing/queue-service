package org.ticketing.queue.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.ticketing.queue.application.dto.command.QueueCreateCommand;
import org.ticketing.queue.application.dto.command.QueueUpdateCommand;
import org.ticketing.queue.application.dto.command.TokenValidateCommand;
import org.ticketing.queue.application.dto.query.QueueListQuery;
import org.ticketing.queue.application.dto.result.QueueListResult;
import org.ticketing.queue.application.dto.result.QueueResult;
import org.ticketing.queue.domain.dto.QueueProjection;
import org.ticketing.queue.domain.exception.QueueException;
import org.ticketing.queue.domain.model.Queue;
import org.ticketing.queue.domain.model.QueueExitReason;
import org.ticketing.queue.domain.model.QueueHistory;
import org.ticketing.queue.domain.model.QueueToken;
import org.ticketing.queue.domain.repository.QueueHistoryRepository;
import org.ticketing.queue.domain.repository.QueueRedisRepository;
import org.ticketing.queue.domain.repository.QueueRepository;
import org.ticketing.queue.domain.service.QueueTokenDomainService;
import org.ticketing.queue.presentation.dto.response.UserStatusResponse;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class QueueService {

    private final QueueRepository queueRepository;
    private final QueueHistoryRepository queueHistoryRepository;
    private final QueueRedisRepository queueRedisRepository;
    private final QueueTokenDomainService queueTokenDomainService;

    // SSE 타임아웃: 15분 (대기열 최대 대기 시간 기준)
    private static final long SSE_TIMEOUT_MS = 15 * 60 * 1000L;

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public QueueResult getQueue(UUID queueId) {
        return QueueResult.from(queueRepository.findById(queueId));
    }

    @Transactional(readOnly = true)
    public QueueListResult getQueueList(QueueListQuery query, Pageable pageable) {
        Page<QueueProjection> projections = queueRepository
                .findAllByCondition(QueueListQuery.toQueueSearchCondition(query), pageable);

        return QueueListResult.from(projections);
    }

    public UUID createQueue(QueueCreateCommand command) {
        if (queueRepository.existsByMatchId(command.matchId())) {
            throw new QueueException("이미 해당 경기의 큐 설정이 존재합니다.", HttpStatus.BAD_REQUEST);
        }

        Queue queue = Queue.create(
                UUID.randomUUID(),
                command.matchId(),
                command.maxActiveUsers(),
                command.openAt()
        );
        return queueRepository.save(queue).getId();
    }

    public void updateQueue(UUID queueId, QueueUpdateCommand command) {
        Queue queue = queueRepository.findByIdAndDeletedAtIsNull(queueId);
        queue.update(
                command.matchId(),
                command.maxActiveUsers(),
                command.status(),
                command.openAt()
        );
    }

    public void deleteQueue(UUID queueId, UUID userId) {
        Queue queue = queueRepository.findByIdAndDeletedAtIsNull(queueId);
        queue.softDelete(userId);
    }

    public void entry(UUID matchId, UUID userId) {
        boolean added = queueRedisRepository.entry(matchId, userId);

        if (!added) {
            throw new QueueException("이미 대기열에서 대기중입니다.", HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 클라이언트 SSE 구독 등록
     * 연결 즉시 현재 상태를 한 번 push하고, 이후 스케줄러가 주기적으로 push
     */
    public SseEmitter subscribe(UUID matchId, UUID userId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        String key = buildKey(matchId, userId);

        // Emitter 생명주기 콜백 등록
        emitter.onCompletion(() -> {
            log.info("[SSE] 연결 완료. key={}", key);
            emitters.remove(buildKey(matchId, userId));
        });
        emitter.onTimeout(() -> {
            log.info("[SSE] 연결 타임아웃. key={}", key);
            LocalDateTime enteredAt = queueRedisRepository.getEnteredAt(matchId, userId);
            recordHistory(matchId, userId, enteredAt, QueueExitReason.TIMEOUT);
            emitters.remove(buildKey(matchId, userId));
            emitter.complete();
        });
        emitter.onError(e -> {
            log.warn("[SSE] 연결 에러. key={}, error={}", key, e.getMessage());
            emitters.remove(buildKey(matchId, userId));
        });

        emitters.put(buildKey(matchId, userId), emitter);

        // 연결 즉시 현재 상태 전송
        pushStatus(matchId, userId, emitter);

        return emitter;
    }

    /**
     * 스케줄러: 3초마다 모든 활성 Emitter에 상태 push
     * - 통과된 유저에게는 토큰을 보내고 연결 종료
     * - 대기 중인 유저에게는 현재 순번 업데이트
     */
    @Scheduled(fixedDelay = 3000)
    public void pushStatusToAll() {
        // matchId 목록 추출 (중복 제거)
        Set<UUID> matchIds = emitters.keySet().stream()
                .map(key -> UUID.fromString(key.split(":")[0]))
                .collect(Collectors.toSet());

        for (UUID matchId : matchIds) {
            List<String> keys = findKeysByMatchId(matchId);

            for (String key : keys) {
                UUID[] ids = parseKey(key);
                UUID userId = ids[1];

                SseEmitter emitter = findByKey(matchId, userId);
                if (emitter == null) continue;

                pushStatus(matchId, userId, emitter);
            }
        }
    }

    private void pushStatus(UUID matchId, UUID userId, SseEmitter emitter) {
        boolean slotAcquired = false;
        boolean tokenAcquired = false;
        LocalDateTime enteredAt = null;

        try {
            Long rank = queueRedisRepository.getRank(matchId, userId);
            Long totalCount = queueRedisRepository.getTotalCount(matchId);
            Long availableSlots = queueRedisRepository.getAvailableSlots(matchId);

            if (rank != null && rank <= availableSlots) {
                // 슬롯 원자적 차감 시도
                slotAcquired = queueRedisRepository.acquireSlot(matchId);
                if (!slotAcquired) {
                    // 슬롯 없음 → 대기 유지
                    sendEvent(emitter, UserStatusResponse.ofWaiting(rank, totalCount));
                    return;
                }

                // 유저별 중복 발급 방지
                tokenAcquired = queueRedisRepository.acquirePassToken(matchId, userId);
                if (!tokenAcquired) {
                    String existingToken = queueRedisRepository.getPassToken(matchId, userId);
                    if (existingToken == null) {
                        sendEvent(emitter, UserStatusResponse.ofWaiting(rank, totalCount));
                        return;
                    }

                    sendEvent(emitter, UserStatusResponse.ofPassed(rank, totalCount, existingToken));
                    emitters.remove(buildKey(matchId, userId));
                    emitter.complete();
                    return;
                }

                // 선점 성공한 스레드만 여기 진입 (단 하나)
                QueueToken token = queueTokenDomainService.issue(matchId, userId);

                // exit() 전에 진입 시각 조회 (exit 후에는 ZSet에서 삭제되어 조회 불가)
                enteredAt = queueRedisRepository.getEnteredAt(matchId, userId);

                // 발급된 토큰으로 placeholder 교체 & 대기열에서 삭제
                queueRedisRepository.savePassToken(matchId, userId, token.getToken());
                queueRedisRepository.exit(matchId, userId);

                // 큐 이탈 시 히스토리 기록
                recordHistory(matchId, userId, enteredAt, QueueExitReason.PASSED);

                sendEvent(emitter, UserStatusResponse.ofPassed(rank, totalCount, token.getToken()));
                emitters.remove(buildKey(matchId, userId));
                emitter.complete();

            } else {
                sendEvent(emitter, UserStatusResponse.ofWaiting(rank, totalCount));
            }

        } catch (IOException e) {
            log.warn("[SSE] 전송 실패. matchId={}, userId={}", matchId, userId);

            if (tokenAcquired) {
                queueRedisRepository.deletePassToken(matchId, userId); // 토큰 키 삭제
            }
            if (slotAcquired) {
                queueRedisRepository.releaseSlot(matchId); // 슬롯 반납
            }
            if (enteredAt == null) {
                enteredAt = queueRedisRepository.getEnteredAt(matchId, userId);
            }
            recordHistory(matchId, userId, enteredAt, QueueExitReason.IO_ERROR);

            emitters.remove(buildKey(matchId, userId));
            emitter.completeWithError(e);

        } catch (Exception e) {
            log.error("[SSE] 예상치 못한 오류. matchId={}, userId={}", matchId, userId, e);

            if (tokenAcquired) {
                queueRedisRepository.deletePassToken(matchId, userId);
            }
            if (slotAcquired) {
                queueRedisRepository.releaseSlot(matchId);
            }
            if (enteredAt == null) {
                enteredAt = queueRedisRepository.getEnteredAt(matchId, userId);
            }
            recordHistory(matchId, userId, enteredAt, QueueExitReason.UNEXPECTED_ERROR);

            emitters.remove(buildKey(matchId, userId));
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
            // 이미 완료된 emitter → 무시
            log.warn("[SSE] 이미 완료된 emitter. 전송 스킵");
        }
    }

    // 대기열 이탈 내역 저장
    @Async
    public void recordHistory(UUID matchId, UUID userId, LocalDateTime enteredAt, QueueExitReason exitReason) {
        QueueHistory history = switch(exitReason) {
            case PASSED -> QueueHistory.ofPassed(matchId, userId, enteredAt);
            case IO_ERROR -> QueueHistory.ofIoError(matchId, userId, enteredAt);
            case UNEXPECTED_ERROR -> QueueHistory.ofUnexpectedError(matchId, userId, enteredAt);
            case TIMEOUT -> QueueHistory.ofTimeout(matchId, userId, enteredAt);
        };

        saveQuietly(history);
    }

    // 저장 실패가 SSE 흐름에 영향을 주지 않도록 예외를 삼킨다
    private void saveQuietly(QueueHistory history) {
        try {
            queueHistoryRepository.save(history);
            log.debug("[QueueHistory] 저장 완료. matchId={}, userId={}, reason={}",
                    history.getMatchId(), history.getUserId(), history.getExitReason());
        } catch (Exception e) {
            log.warn("[QueueHistory] 저장 실패. matchId={}, userId={}, reason={}",
                    history.getMatchId(), history.getUserId(), history.getExitReason(), e);
        }
    }

    private String buildKey(UUID matchId, UUID userId) {
        return matchId + ":" + userId;
    }

    private UUID[] parseKey(String key) {
        String[] parts = key.split(":");
        return new UUID[]{ UUID.fromString(parts[0]), UUID.fromString(parts[1]) };
    }

    public SseEmitter findByKey(UUID matchId, UUID userId) {
        return emitters.get(buildKey(matchId, userId));
    }

    public List<String> findKeysByMatchId(UUID matchId) {
        String prefix = matchId + ":";
        return emitters.keySet().stream()
                .filter(k -> k.startsWith(prefix))
                .toList();
    }

    public void validate(TokenValidateCommand command) {
        String token = queueRedisRepository.findPassToken(command.matchId(), command.userId());
        LocalDateTime expiredAt = queueRedisRepository.getExpiredAt(command.matchId(), command.userId());

        // 저장된 accessToken 불일치
        if (!token.equals(command.accessToken())) {
            throw new QueueException("토큰이 불일치합니다.", HttpStatus.BAD_REQUEST);
        }
        // 만료 시간 체크
        if (expiredAt.isBefore(LocalDateTime.now())) {
            throw new QueueException("만료된 토큰입니다.", HttpStatus.REQUEST_TIMEOUT);
        }
    }
}
