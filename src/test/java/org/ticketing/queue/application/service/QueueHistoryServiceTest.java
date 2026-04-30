package org.ticketing.queue.application.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ticketing.queue.domain.model.QueueExitReason;
import org.ticketing.queue.domain.model.QueueHistory;
import org.ticketing.queue.domain.repository.QueueHistoryRepository;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QueueHistoryServiceTest {

    @Mock
    private QueueHistoryRepository queueHistoryRepository;

    @InjectMocks
    private QueueHistoryService queueHistoryService;

    @Nested
    @DisplayName("QueueHistory 기록 테스트")
    class RecordTest {

        @Test
        @DisplayName("PASSED 상태 저장 성공")
        void record_passed_success() {
            // given
            UUID matchId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            LocalDateTime enteredAt = LocalDateTime.now();

            // when
            queueHistoryService.record(matchId, userId, enteredAt, QueueExitReason.PASSED);

            // then
            verify(queueHistoryRepository, times(1)).save(any(QueueHistory.class));
        }

        @Test
        @DisplayName("IO_ERROR 상태 저장 성공")
        void record_ioError_success() {
            // given
            UUID matchId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            // when
            queueHistoryService.record(
                    matchId,
                    userId,
                    LocalDateTime.now(),
                    QueueExitReason.IO_ERROR
            );

            // then
            verify(queueHistoryRepository).save(any(QueueHistory.class));
        }

        @Test
        @DisplayName("예외 발생 시 저장 실패 로그만 남기고 종료")
        void record_saveFail_ignoreException() {
            // given
            UUID matchId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            doThrow(new RuntimeException("DB 저장 실패"))
                    .when(queueHistoryRepository)
                    .save(any(QueueHistory.class));

            // when
            queueHistoryService.record(
                    matchId,
                    userId,
                    LocalDateTime.now(),
                    QueueExitReason.TIMEOUT
            );

            // then
            verify(queueHistoryRepository, times(1))
                    .save(any(QueueHistory.class));
        }
    }
}