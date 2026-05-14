package org.ticketing.queue.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.ticketing.queue.application.dto.command.QueueCreateCommand;
import org.ticketing.queue.application.dto.command.QueueUpdateCommand;
import org.ticketing.queue.application.dto.command.TokenValidateCommand;
import org.ticketing.queue.application.dto.query.QueueListQuery;
import org.ticketing.queue.application.dto.result.QueueListResult;
import org.ticketing.queue.application.dto.result.QueueResult;
import org.ticketing.queue.domain.dto.QueueProjection;
import org.ticketing.queue.domain.exception.AlreadyBannedUserException;
import org.ticketing.queue.domain.exception.AlreadyInitQueueException;
import org.ticketing.queue.domain.exception.AlreadyWaitingQueueException;
import org.ticketing.queue.domain.exception.TokenException;
import org.ticketing.queue.domain.model.BannedUser;
import org.ticketing.queue.domain.model.Queue;
import org.ticketing.queue.domain.model.QueueExitReason;
import org.ticketing.queue.domain.repository.BannedUserRepository;
import org.ticketing.queue.domain.repository.QueueRedisRepository;
import org.ticketing.queue.domain.repository.QueueRepository;
import org.ticketing.queue.infrastructure.persistence.SseEmitterRepository;
import org.ticketing.queue.infrastructure.redis.pubsub.QueueRedisSubscriber;
import org.ticketing.queue.presentation.dto.response.UserStatusResponse;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class QueueService {

    private final QueueHistoryService queueHistoryService;
    private final QueueRepository queueRepository;
    private final QueueRedisRepository queueRedisRepository;
    private final SseEmitterRepository sseEmitterRepository; // ← ConcurrentHashMap 대체
    private final QueueRedisSubscriber queueRedisSubscriber;
    private final BannedUserRepository bannedUserRepository;

    // SSE 타임아웃: 15분 (대기열 최대 대기 시간 기준)
    private static final long SSE_TIMEOUT_MS = 15 * 60 * 1000L;

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
            throw new AlreadyInitQueueException(command.matchId());
        }

        Queue queue = Queue.create(
                UUID.randomUUID(),
                command.matchId(),
                command.maxActiveUsers(),
                command.openAt(),
                command.expiredAt()
        );
        return queueRepository.save(queue).getId();
    }

    public void updateQueue(UUID queueId, QueueUpdateCommand command) {
        Queue queue = queueRepository.findByIdAndDeletedAtIsNull(queueId);
        queue.update(
                command.matchId(),
                command.maxActiveUsers(),
                command.status(),
                command.openAt(),
                command.expiredAt()
        );
    }

    public void deleteQueue(UUID queueId, UUID userId) {
        Queue queue = queueRepository.findByIdAndDeletedAtIsNull(queueId);
        queue.softDelete(userId);
    }

    // 유저 대기열 진입(중복 진입 불가)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void entry(UUID matchId, UUID userId) {
        // 토큰 보유 유저는 재진입 불가
        String existingToken = queueRedisRepository.getPassToken(matchId, userId);
        if (existingToken != null) {
            throw new TokenException(String.format("이미 발급된 토큰이 있습니다. matchId = %s, userId = %s", matchId, userId));
        }

        boolean added = queueRedisRepository.entry(matchId, userId);

        if (!added) {
            throw new AlreadyWaitingQueueException(matchId, userId);
        }
    }

    /**
     * 클라이언트 SSE 구독 등록
     * 연결 즉시 현재 상태를 한 번 push하고, 이후 스케줄러가 주기적으로 push
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public SseEmitter subscribe(UUID matchId, UUID userId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        emitter.onCompletion(() -> {
            log.info("[SSE] 연결 완료. matchId={}, userId={}", matchId, userId);
            sseEmitterRepository.remove(matchId, userId);
        });
        emitter.onTimeout(() -> {
            log.info("[SSE] 연결 타임아웃. matchId={}, userId={}", matchId, userId);
            LocalDateTime enteredAt = queueRedisRepository.getEnteredAt(matchId, userId);
            queueHistoryService.record(matchId, userId, enteredAt, QueueExitReason.TIMEOUT);
            sseEmitterRepository.remove(matchId, userId);
        });
        emitter.onError(e -> {
            log.warn("[SSE] 연결 에러. matchId={}, userId={}", matchId, userId);
            sseEmitterRepository.remove(matchId, userId);
        });

        sseEmitterRepository.save(matchId, userId, emitter);

        try {
            // 토큰 보유 유저 → 즉시 토큰 전송 후 대기열 제거
            String existingToken = queueRedisRepository.getPassToken(matchId, userId);
            if (existingToken != null) {
                log.info("[SSE] 토큰 보유 유저 재접속. 즉시 토큰 전송. matchId={}, userId={}", matchId, userId);
                try {
                    sendEvent(emitter, UserStatusResponse.ofIssued(existingToken));
                } catch (IOException e) {
                    log.warn("[SSE] 토큰 즉시 전송 실패. matchId={}, userId={}", matchId, userId);
                } finally {
                    queueRedisRepository.exit(matchId, userId);
                    emitter.complete(); // onCompletion → remove()
                }
                return emitter;
            }

            // 구독 즉시 슬롯 비교 → 범위 내면 바로 토큰 발급
            Long rank = queueRedisRepository.getRank(matchId, userId);
            Long totalCount = queueRedisRepository.getTotalCount(matchId);

            queueRedisSubscriber.pushStatus(matchId, userId, emitter, rank, totalCount);

        } catch (Exception e) {
            log.error("[SSE] 구독 처리 중 예외 발생. matchId={}, userId={}", matchId, userId, e);
            try {
                emitter.send(
                        SseEmitter.event()
                                .name("error")
                                .data("서버 오류가 발생했습니다.")
                );
            } catch (IOException sendEx) {
                log.warn("[SSE] 에러 이벤트 전송 실패. matchId={}, userId={}", matchId, userId, sendEx);
            } finally {
                emitter.completeWithError(e);
            }
        }

        return emitter;
    }

    /**
     * 스케줄러: 3초마다 모든 활성 Emitter에 상태 push
     * - 순위 업데이트만 처리
     */
    @Scheduled(fixedDelay = 3000)
    public void pushStatusToAll() {
        Set<UUID> matchIds = sseEmitterRepository.getAllMatchIds();

        for (UUID matchId : matchIds) {
            Long totalCount = queueRedisRepository.getTotalCount(matchId);
            List<UUID> userIds = sseEmitterRepository.findUserIdsByMatchId(matchId);
            if (userIds.isEmpty()) continue;

            Map<UUID, Long> ranks = queueRedisRepository.getRankBatch(matchId, userIds);

            // parallel stream으로 변경
            userIds.parallelStream().forEach(userId -> {
                SseEmitter emitter = sseEmitterRepository.find(matchId, userId);
                if (emitter == null) return;

                // rank == null, 이미 exit() 됐는데 emitter만 남은 경우 → 정리
                Long rank = ranks.get(userId);
                if (rank == null) {
                    emitter.complete(); // onCompletion → remove()
                    return;
                }

                // 무조건 pushStatus → Lua 스크립트가 슬롯 판단
                queueRedisSubscriber.pushStatus(matchId, userId, emitter, rank, totalCount);
            });
        }
    }

    // SSE 전송
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
            // 이미 완료된 emitter → IOException으로 변환해 호출부에서 처리
            throw new IOException("Emitter already completed", e);
        }
    }

    // 토큰 검증
    public void validate(TokenValidateCommand command) {
        String token = queueRedisRepository.findPassToken(command.matchId(), command.userId());
        LocalDateTime expiredAt = queueRedisRepository.getExpiredAt(command.matchId(), command.userId());

        // 저장된 accessToken 불일치
        if (!token.equals(command.accessToken())) {
            throw new TokenException("토큰이 불일치합니다.");
        }
        // 만료 시간 체크
        if (expiredAt.isBefore(LocalDateTime.now())) {
            throw new TokenException("만료된 토큰입니다.");
        }
    }

    /**
     * 대기열 초기화
     * 1. Queue 엔티티 조회 및 검증
     * 2. SSE 연결 유저 전체에 초기화 이벤트 전송 후 연결 종료
     * 3. Redis 데이터 전체 삭제 (sortedSet / 슬롯 / 토큰)
     * 4. 이력 기록 (REFRESH 사유)
     */
    public void refreshQueue(UUID matchId) {
        // SSE 연결 유저 조회 → 이력 기록 (Redis 삭제 전에 먼저 수행)
        recordHistoryForConnectedUsers(matchId);

        // SSE 초기화 이벤트 전송 및 연결 종료
        notifyRefreshAndCloseEmitters(matchId);

        // Redis 전체 초기화
        queueRedisRepository.refreshQueue(matchId);

        log.info("[Queue] 대기열 초기화 완료. matchId={}", matchId);
    }

    /**
     * SSE 연결 유저 → 이력 기록
     * Redis 삭제 전에 enteredAt 조회해야 하므로 먼저 처리
     */
    private void recordHistoryForConnectedUsers(UUID matchId) {
        List<UUID> connectedUserIds = sseEmitterRepository.findUserIdsByMatchId(matchId);

        for (UUID userId : connectedUserIds) {
            try {
                LocalDateTime enteredAt = queueRedisRepository.getEnteredAt(matchId, userId);
                queueHistoryService.record(matchId, userId, enteredAt, QueueExitReason.REFRESH);
            } catch (Exception e) {
                // 이력 기록 실패가 초기화를 막으면 안 됨
                log.warn("[Queue] 이력 기록 실패. matchId={}, userId={}", matchId, userId, e);
            }
        }
    }

    // SSE 연결 유저 전체에 초기화 이벤트 전송 후 연결 종료
    private void notifyRefreshAndCloseEmitters(UUID matchId) {
        List<UUID> connectedUserIds = sseEmitterRepository.findUserIdsByMatchId(matchId);

        for (UUID userId : connectedUserIds) {
            SseEmitter emitter = sseEmitterRepository.find(matchId, userId);
            if (emitter == null) continue;

            try {
                emitter.send(
                        SseEmitter.event()
                                .name("queue-refresh")
                                .data(UserStatusResponse.ofRefreshed())
                                .id(String.valueOf(System.currentTimeMillis()))
                );
            } catch (IOException e) {
                log.warn("[SSE] 초기화 이벤트 전송 실패. matchId={}, userId={}", matchId, userId);
            } finally {
                emitter.complete();
            }
        }
    }

    /**
     * 특정 유저 차단
     * 1. Queue 엔티티 조회
     * 2. 중복 차단 검증
     * 3. 대기열에 있는 경우 → 이력 기록 후 Redis에서 제거
     * 4. SSE 연결 있는 경우 → 차단 이벤트 전송 후 연결 종료
     * 5. 통과 토큰 보유 중인 경우 → 토큰 삭제 및 슬롯 반환
     * 6. BannedUser 저장
     */
    public void banUser(UUID matchId, UUID userId) {
        // 중복 차단 검증
        if (bannedUserRepository.existsByQueueIdAndUserId(matchId, userId)) {
            throw new AlreadyBannedUserException(matchId, userId);
        }

        // 대기열에 존재하면 이력 기록 후 제거
        removeFromQueueIfPresent(matchId, userId);

        // SSE 연결 종료
        notifyBannedAndCloseEmitter(matchId, userId);

        // BannedUser 저장
        BannedUser bannedUser = BannedUser.create(matchId, userId);
        bannedUserRepository.save(bannedUser);

        // Redis에 차단 정보 저장 → entry() 시 차단 체크용
        queueRedisRepository.saveBannedUser(matchId, userId);

        log.info("[Queue] 유저 차단 완료. matchId = {}, userId = {}", matchId, userId);
    }

    /**
     * 대기열 존재 여부 확인 후 이력 기록 및 제거
     * 통과 토큰 보유 시 토큰 삭제 + 슬롯 반환
     */
    private void removeFromQueueIfPresent(UUID matchId, UUID userId) {
        Long rank = queueRedisRepository.getRank(matchId, userId);

        if (rank != null) {
            // 대기 중인 유저 → 이력 기록
            try {
                LocalDateTime enteredAt = queueRedisRepository.getEnteredAt(matchId, userId);
                queueHistoryService.record(matchId, userId, enteredAt, QueueExitReason.BANNED);
            } catch (Exception e) {
                log.warn("[Queue] 차단 이력 기록 실패. matchId={}, userId={}", matchId, userId, e);
            }
            // sortedSet에서 제거
            queueRedisRepository.exit(matchId, userId);
        }

        // 통과 토큰 보유 여부 확인 → 토큰 삭제 + 슬롯 반환
        String passToken = queueRedisRepository.getPassToken(matchId, userId);
        if (passToken != null) {
            queueRedisRepository.deletePassToken(matchId, userId);
            queueRedisRepository.releaseSlot(matchId);
            log.info("[Queue] 차단 유저 토큰 삭제 및 슬롯 반환. matchId={}, userId={}", matchId, userId);
        }
    }

    /**
     * SSE 연결된 유저에게 차단 이벤트 전송 후 연결 종료
     */
    private void notifyBannedAndCloseEmitter(UUID matchId, UUID userId) {
        SseEmitter emitter = sseEmitterRepository.find(matchId, userId);
        if (emitter == null) return;

        try {
            emitter.send(
                    SseEmitter.event()
                            .name("queue-banned")
                            .data(UserStatusResponse.ofBanned())
                            .id(String.valueOf(System.currentTimeMillis()))
            );
        } catch (IOException e) {
            log.warn("[SSE] 차단 이벤트 전송 실패. matchId={}, userId={}", matchId, userId);
        } finally {
            emitter.complete();
            sseEmitterRepository.remove(matchId, userId);
        }
    }
}
