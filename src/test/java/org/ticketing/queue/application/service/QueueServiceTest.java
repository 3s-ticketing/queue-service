package org.ticketing.queue.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.ticketing.queue.application.dto.command.QueueCreateCommand;
import org.ticketing.queue.application.dto.command.TokenValidateCommand;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QueueServiceTest {

    @Mock
    private QueueHistoryService queueHistoryService;

    @Mock
    private QueueRepository queueRepository;

    @Mock
    private QueueRedisRepository queueRedisRepository;

    @Mock
    private BannedUserRepository bannedUserRepository;

    @Mock
    private SseEmitterRepository sseEmitterRepository;

    @Mock
    private QueueRedisSubscriber queueRedisSubscriber;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private QueueService queueService;

    @Nested
    @DisplayName("대기열 생성 테스트")
    class CreateQueueTest {

        @Test
        @DisplayName("정상 생성")
        void createQueue_success() {
            // given
            UUID matchId = UUID.randomUUID();

            QueueCreateCommand command = new QueueCreateCommand(
                    matchId,
                    100,
                    LocalDateTime.now().plusHours(1),
                    LocalDateTime.now().plusHours(2)
            );

            Queue queue = Queue.create(
                    UUID.randomUUID(),
                    command.matchId(),
                    command.maxActiveUsers(),
                    command.openAt(),
                    command.expiredAt()
            );

            when(queueRepository.existsByMatchId(matchId)).thenReturn(false);
            when(queueRepository.save(any(Queue.class))).thenReturn(queue);

            // when
            UUID result = queueService.createQueue(command);

            // then
            assertThat(result).isEqualTo(queue.getId());
            verify(queueRepository).save(any(Queue.class));
        }

        @Test
        @DisplayName("이미 존재하면 예외 발생")
        void createQueue_alreadyExists() {
            // given
            UUID matchId = UUID.randomUUID();

            QueueCreateCommand command = new QueueCreateCommand(
                    matchId,
                    100,
                    LocalDateTime.now(),
                    LocalDateTime.now().plusHours(2)
            );

            when(queueRepository.existsByMatchId(matchId)).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> queueService.createQueue(command))
                    .isInstanceOf(AlreadyInitQueueException.class);
        }
    }

    @Nested
    @DisplayName("대기열 입장 테스트")
    class EntryTest {

        @Test
        @DisplayName("정상 입장")
        void entry_success() {
            // given
            UUID matchId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            when(queueRedisRepository.entry(matchId, userId)).thenReturn(true);

            // when
            queueService.entry(matchId, userId);

            // then
            verify(queueRedisRepository).entry(matchId, userId);
        }

        @Test
        @DisplayName("중복 입장 시 예외")
        void entry_duplicate() {
            // given
            UUID matchId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            when(queueRedisRepository.entry(matchId, userId)).thenReturn(false);

            // when & then
            assertThatThrownBy(() -> queueService.entry(matchId, userId))
                    .isInstanceOf(AlreadyWaitingQueueException.class);
        }
    }

    @Nested
    @DisplayName("토큰 검증 테스트")
    class ValidateTest {

        @Test
        @DisplayName("정상 토큰")
        void validate_success() {
            // given
            UUID matchId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            String token = "valid-token";

            TokenValidateCommand command = new TokenValidateCommand(matchId, userId, token);

            when(queueRedisRepository.findPassToken(matchId, userId))
                    .thenReturn(token);

            when(queueRedisRepository.getExpiredAt(matchId, userId))
                    .thenReturn(LocalDateTime.now().plusMinutes(10));

            // when & then
            assertThatCode(() -> queueService.validate(command))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("토큰 불일치")
        void validate_invalidToken() {
            // given
            UUID matchId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            TokenValidateCommand command = new TokenValidateCommand(matchId, userId, "wrong-token");

            when(queueRedisRepository.findPassToken(matchId, userId))
                    .thenReturn("real-token");

            when(queueRedisRepository.getExpiredAt(matchId, userId))
                    .thenReturn(LocalDateTime.now().plusMinutes(10));

            // when & then
            assertThatThrownBy(() -> queueService.validate(command))
                    .isInstanceOf(TokenException.class)
                    .hasMessageContaining("토큰이 불일치합니다");
        }

        @Test
        @DisplayName("만료 토큰")
        void validate_expiredToken() {
            // given
            UUID matchId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            String token = "expired-token";

            TokenValidateCommand command = new TokenValidateCommand(matchId, userId, token);

            when(queueRedisRepository.findPassToken(matchId, userId))
                    .thenReturn(token);

            when(queueRedisRepository.getExpiredAt(matchId, userId))
                    .thenReturn(LocalDateTime.now().minusMinutes(1));

            // when & then
            assertThatThrownBy(() -> queueService.validate(command))
                    .isInstanceOf(TokenException.class)
                    .hasMessageContaining("만료된 토큰입니다");
        }
    }

    @Nested
    @DisplayName("SSE 구독 테스트")
    class SubscribeTest {

        @Test
        @DisplayName("즉시 대기 상태 전송")
        void subscribe_waiting() throws Exception {
            // given
            UUID matchId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            // rank(10) > availableSlots(5) → else 분기 → sendEvent() 호출
            when(queueRedisRepository.getRank(matchId, userId)).thenReturn(10L);
            when(queueRedisRepository.getTotalCount(matchId)).thenReturn(100L);
            when(queueRedisRepository.getAvailableSlots(matchId)).thenReturn(5L);

            when(objectMapper.writeValueAsString(any()))
                    .thenReturn("{\"status\":\"WAITING\",\"rank\":10,\"totalCount\":100}");

            doNothing().when(sseEmitterRepository)
                    .save(eq(matchId), eq(userId), any(SseEmitter.class));

            // when
            SseEmitter emitter = queueService.subscribe(matchId, userId);

            // then
            assertThat(emitter).isNotNull();

            verify(sseEmitterRepository).save(eq(matchId), eq(userId), any(SseEmitter.class));
            verify(queueRedisSubscriber, never())
                    .pushStatus(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("슬롯 범위 내 즉시 토큰 발급 시도")
        void subscribe_immediateTokenIssue() throws Exception {
            // given
            UUID matchId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            // rank(3) <= availableSlots(5) → if 분기 → pushStatus() 호출
            when(queueRedisRepository.getRank(matchId, userId)).thenReturn(3L);
            when(queueRedisRepository.getTotalCount(matchId)).thenReturn(100L);
            when(queueRedisRepository.getAvailableSlots(matchId)).thenReturn(5L);

            doNothing().when(sseEmitterRepository)
                    .save(eq(matchId), eq(userId), any(SseEmitter.class));

            // when
            SseEmitter emitter = queueService.subscribe(matchId, userId);

            // then
            assertThat(emitter).isNotNull();

            verify(sseEmitterRepository).save(eq(matchId), eq(userId), any(SseEmitter.class));
            verify(queueRedisSubscriber)
                    .pushStatus(eq(matchId), eq(userId), any(SseEmitter.class), eq(3L), eq(100L));
        }
    }

    @Nested
    @DisplayName("대기열 초기화")
    class RefreshQueueTest {

        @Test
        @DisplayName("연결된 모든 유저의 이력을 기록하고 SSE 종료 후 Redis 초기화한다")
        void refreshQueue_success() throws Exception {
            // given
            UUID matchId = UUID.randomUUID();
            UUID user1 = UUID.randomUUID();
            UUID user2 = UUID.randomUUID();

            List<UUID> connectedUsers = List.of(user1, user2);

            SseEmitter emitter1 = mock(SseEmitter.class);
            SseEmitter emitter2 = mock(SseEmitter.class);

            when(sseEmitterRepository.findUserIdsByMatchId(matchId))
                    .thenReturn(connectedUsers);

            when(sseEmitterRepository.find(matchId, user1))
                    .thenReturn(emitter1);

            when(sseEmitterRepository.find(matchId, user2))
                    .thenReturn(emitter2);

            when(queueRedisRepository.getEnteredAt(matchId, user1))
                    .thenReturn(LocalDateTime.now().minusMinutes(5));

            when(queueRedisRepository.getEnteredAt(matchId, user2))
                    .thenReturn(LocalDateTime.now().minusMinutes(3));

            when(objectMapper.writeValueAsString(any()))
                    .thenReturn("{\"status\":\"REFRESHED\"}");

            // when
            queueService.refreshQueue(matchId);

            // then
            verify(queueHistoryService).record(
                    eq(matchId), eq(user1), any(LocalDateTime.class), eq(QueueExitReason.REFRESH)
            );
            verify(queueHistoryService).record(
                    eq(matchId), eq(user2), any(LocalDateTime.class), eq(QueueExitReason.REFRESH)
            );

            verify(emitter1).send(any(SseEmitter.SseEventBuilder.class));
            verify(emitter2).send(any(SseEmitter.SseEventBuilder.class));

            verify(emitter1).complete();
            verify(emitter2).complete();

            verify(queueRedisRepository).refreshQueue(matchId);
        }
    }

    @Nested
    @DisplayName("유저 차단")
    class BanUserTest {

        @Test
        @DisplayName("대기열 유저 차단 성공")
        void banUser_success_waitingUser() throws Exception {
            // given
            UUID matchId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            SseEmitter emitter = mock(SseEmitter.class);

            when(bannedUserRepository.existsByQueueIdAndUserId(matchId, userId))
                    .thenReturn(false);

            when(queueRedisRepository.getRank(matchId, userId))
                    .thenReturn(1L);

            when(queueRedisRepository.getEnteredAt(matchId, userId))
                    .thenReturn(LocalDateTime.now().minusMinutes(10));

            when(queueRedisRepository.getPassToken(matchId, userId))
                    .thenReturn(null);

            when(sseEmitterRepository.find(matchId, userId))
                    .thenReturn(emitter);

            when(objectMapper.writeValueAsString(any()))
                    .thenReturn("{\"status\":\"BANNED\"}");

            // when
            queueService.banUser(matchId, userId);

            // then
            verify(queueHistoryService).record(
                    eq(matchId),
                    eq(userId),
                    any(LocalDateTime.class),
                    eq(QueueExitReason.BANNED)
            );

            verify(queueRedisRepository).exit(matchId, userId);

            verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
            verify(emitter).complete();

            verify(bannedUserRepository).save(any(BannedUser.class));
        }

        @Test
        @DisplayName("통과 토큰 보유 유저 차단 시 토큰 삭제 및 슬롯 반환")
        void banUser_success_passTokenUser() {
            // given
            UUID matchId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            when(bannedUserRepository.existsByQueueIdAndUserId(matchId, userId))
                    .thenReturn(false);

            when(queueRedisRepository.getRank(matchId, userId))
                    .thenReturn(null);

            when(queueRedisRepository.getPassToken(matchId, userId))
                    .thenReturn("PASS_TOKEN");

            when(sseEmitterRepository.find(matchId, userId))
                    .thenReturn(null);

            // when
            queueService.banUser(matchId, userId);

            // then
            verify(queueRedisRepository).deletePassToken(matchId, userId);
            verify(queueRedisRepository).releaseSlot(matchId);

            verify(bannedUserRepository).save(any(BannedUser.class));
        }

        @Test
        @DisplayName("이미 차단된 유저면 예외 발생")
        void banUser_alreadyBanned() {
            // given
            UUID matchId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            when(bannedUserRepository.existsByQueueIdAndUserId(matchId, userId))
                    .thenReturn(true);

            // when & then
            assertThatThrownBy(() -> queueService.banUser(matchId, userId))
                    .isInstanceOf(AlreadyBannedUserException.class);

            verify(bannedUserRepository, never()).save(any());
            verify(queueRedisRepository, never()).exit(any(), any());
        }
    }
}