package org.ticketing.queue.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class QueueHistoryTest {

    @Nested
    @DisplayName("QueueHistory 정적 팩토리 메서드 테스트")
    class FactoryMethodTest {

        @Test
        @DisplayName("ofPassed - 정상 통과 이력을 생성한다")
        void ofPassed_success() {
            // given
            UUID matchId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            LocalDateTime enteredAt = LocalDateTime.now().minusMinutes(10);

            // when
            QueueHistory history = QueueHistory.ofPassed(matchId, userId, enteredAt);

            // then
            assertThat(history.getId()).isNotNull();
            assertThat(history.getMatchId()).isEqualTo(matchId);
            assertThat(history.getUserId()).isEqualTo(userId);
            assertThat(history.getEnteredAt()).isEqualTo(enteredAt);
            assertThat(history.getExitedAt()).isNotNull();
            assertThat(history.getExitReason()).isEqualTo(QueueExitReason.PASSED);
        }

        @Test
        @DisplayName("ofIoError - IO 오류 이력을 생성한다")
        void ofIoError_success() {
            // given
            UUID matchId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            LocalDateTime enteredAt = LocalDateTime.now().minusMinutes(5);

            // when
            QueueHistory history = QueueHistory.ofIoError(matchId, userId, enteredAt);

            // then
            assertThat(history.getId()).isNotNull();
            assertThat(history.getMatchId()).isEqualTo(matchId);
            assertThat(history.getUserId()).isEqualTo(userId);
            assertThat(history.getEnteredAt()).isEqualTo(enteredAt);
            assertThat(history.getExitedAt()).isNotNull();
            assertThat(history.getExitReason()).isEqualTo(QueueExitReason.IO_ERROR);
        }

        @Test
        @DisplayName("ofUnexpectedError - 예상치 못한 오류 이력을 생성한다")
        void ofUnexpectedError_success() {
            // given
            UUID matchId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            LocalDateTime enteredAt = LocalDateTime.now().minusMinutes(3);

            // when
            QueueHistory history = QueueHistory.ofUnexpectedError(matchId, userId, enteredAt);

            // then
            assertThat(history.getId()).isNotNull();
            assertThat(history.getMatchId()).isEqualTo(matchId);
            assertThat(history.getUserId()).isEqualTo(userId);
            assertThat(history.getEnteredAt()).isEqualTo(enteredAt);
            assertThat(history.getExitedAt()).isNotNull();
            assertThat(history.getExitReason()).isEqualTo(QueueExitReason.UNEXPECTED_ERROR);
        }

        @Test
        @DisplayName("ofTimeout - 타임아웃 이력을 생성한다")
        void ofTimeout_success() {
            // given
            UUID matchId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            LocalDateTime enteredAt = LocalDateTime.now().minusMinutes(15);

            // when
            QueueHistory history = QueueHistory.ofTimeout(matchId, userId, enteredAt);

            // then
            assertThat(history.getId()).isNotNull();
            assertThat(history.getMatchId()).isEqualTo(matchId);
            assertThat(history.getUserId()).isEqualTo(userId);
            assertThat(history.getEnteredAt()).isEqualTo(enteredAt);
            assertThat(history.getExitedAt()).isNotNull();
            assertThat(history.getExitReason()).isEqualTo(QueueExitReason.TIMEOUT);
        }


        @Test
        @DisplayName("ofRefreshed - 초기화 이력을 생성한다")
        void ofRefreshed_success() {
            // given
            UUID matchId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            LocalDateTime enteredAt = LocalDateTime.now().minusMinutes(15);

            // when
            QueueHistory history = QueueHistory.ofRefresh(matchId, userId, enteredAt);

            // then
            assertThat(history.getId()).isNotNull();
            assertThat(history.getMatchId()).isEqualTo(matchId);
            assertThat(history.getUserId()).isEqualTo(userId);
            assertThat(history.getEnteredAt()).isEqualTo(enteredAt);
            assertThat(history.getExitedAt()).isNotNull();
            assertThat(history.getExitReason()).isEqualTo(QueueExitReason.REFRESH);
        }

        @Test
        @DisplayName("ofBanned - 차단 이력을 생성한다")
        void ofBanned_success() {
            // given
            UUID matchId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            LocalDateTime enteredAt = LocalDateTime.now().minusMinutes(15);

            // when
            QueueHistory history = QueueHistory.ofBanned(matchId, userId, enteredAt);

            // then
            assertThat(history.getId()).isNotNull();
            assertThat(history.getMatchId()).isEqualTo(matchId);
            assertThat(history.getUserId()).isEqualTo(userId);
            assertThat(history.getEnteredAt()).isEqualTo(enteredAt);
            assertThat(history.getExitedAt()).isNotNull();
            assertThat(history.getExitReason()).isEqualTo(QueueExitReason.BANNED);
        }
    }
}