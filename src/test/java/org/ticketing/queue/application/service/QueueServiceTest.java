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
import org.ticketing.queue.domain.exception.AlreadyInitQueueException;
import org.ticketing.queue.domain.exception.AlreadyWatingQueueException;
import org.ticketing.queue.domain.exception.TokenException;
import org.ticketing.queue.domain.model.Queue;
import org.ticketing.queue.domain.repository.QueueRedisRepository;
import org.ticketing.queue.domain.repository.QueueRepository;
import org.ticketing.queue.infrastructure.persistence.SseEmitterRepository;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueueServiceTest {

    @Mock
    private QueueRepository queueRepository;

    @Mock
    private QueueRedisRepository queueRedisRepository;

    @Mock
    private SseEmitterRepository sseEmitterRepository;

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
                    LocalDateTime.now().plusHours(1)
            );

            Queue queue = Queue.create(
                    UUID.randomUUID(),
                    command.matchId(),
                    command.maxActiveUsers(),
                    command.openAt()
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
                    LocalDateTime.now()
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
                    .isInstanceOf(AlreadyWatingQueueException.class);
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

            when(queueRedisRepository.getRank(matchId, userId)).thenReturn(10L);
            when(queueRedisRepository.getTotalCount(matchId)).thenReturn(100L);
            when(queueRedisRepository.getAvailableSlots(matchId)).thenReturn(5L);

            when(objectMapper.writeValueAsString(any()))
                    .thenReturn("{\"status\":\"WAITING\"}");

            // when
            SseEmitter emitter = queueService.subscribe(matchId, userId);

            // then
            assertThat(emitter).isNotNull();

            verify(sseEmitterRepository).save(eq(matchId), eq(userId), any(SseEmitter.class));
        }
    }
}