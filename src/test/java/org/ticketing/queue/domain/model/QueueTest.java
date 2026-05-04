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
class QueueTest {

    @Nested
    @DisplayName("Queue 생성 테스트")
    class CreateTest {

        @Test
        @DisplayName("create - 대기열을 생성한다")
        void create_success() {
            // given
            UUID queueId = UUID.randomUUID();
            UUID matchId = UUID.randomUUID();
            int maxActiveUsers = 100;
            LocalDateTime openAt = LocalDateTime.now().plusHours(1);
            LocalDateTime expiredAt = LocalDateTime.now().plusHours(2);

            // when
            Queue queue = Queue.create(queueId, matchId, maxActiveUsers, openAt, expiredAt);

            // then
            assertThat(queue.getId()).isEqualTo(queueId);
            assertThat(queue.getMatchId()).isEqualTo(matchId);
            assertThat(queue.getMaxActiveUsers()).isEqualTo(maxActiveUsers);
            assertThat(queue.getOpenAt()).isEqualTo(openAt);
            assertThat(queue.getStatus()).isEqualTo(QueueStatus.CLOSED);
        }
    }

    @Nested
    @DisplayName("Queue 상태 변경 테스트")
    class StatusChangeTest {

        @Test
        @DisplayName("activate - ACTIVE 상태로 변경한다")
        void activate_success() {
            // given
            Queue queue = createQueue();

            // when
            queue.activate();

            // then
            assertThat(queue.getStatus()).isEqualTo(QueueStatus.ACTIVE);
        }

        @Test
        @DisplayName("close - CLOSED 상태로 변경한다")
        void close_success() {
            // given
            Queue queue = createQueue();
            queue.activate();

            // when
            queue.close();

            // then
            assertThat(queue.getStatus()).isEqualTo(QueueStatus.CLOSED);
        }

        @Test
        @DisplayName("ready - READY 상태로 변경한다")
        void ready_success() {
            // given
            Queue queue = createQueue();

            // when
            queue.ready();

            // then
            assertThat(queue.getStatus()).isEqualTo(QueueStatus.READY);
        }
    }

    @Nested
    @DisplayName("Queue 수정 테스트")
    class UpdateTest {

        @Test
        @DisplayName("update - 대기열 정보를 수정한다")
        void update_success() {
            // given
            Queue queue = createQueue();

            UUID newMatchId = UUID.randomUUID();
            int newMaxActiveUsers = 300;
            LocalDateTime newOpenAt = LocalDateTime.now().plusDays(1);
            LocalDateTime expiredAt = LocalDateTime.now().plusDays(2);

            // when
            queue.update(
                    newMatchId,
                    newMaxActiveUsers,
                    QueueStatus.ACTIVE,
                    newOpenAt,
                    expiredAt
            );

            // then
            assertThat(queue.getMatchId()).isEqualTo(newMatchId);
            assertThat(queue.getMaxActiveUsers()).isEqualTo(newMaxActiveUsers);
            assertThat(queue.getStatus()).isEqualTo(QueueStatus.ACTIVE);
            assertThat(queue.getOpenAt()).isEqualTo(newOpenAt);
            assertThat(queue.getExpiredAt()).isEqualTo(expiredAt);
        }
    }

    @Nested
    @DisplayName("Queue 삭제 테스트")
    class SoftDeleteTest {

        @Test
        @DisplayName("softDelete - 삭제 처리한다")
        void softDelete_success() {
            // given
            Queue queue = createQueue();
            UUID userId = UUID.randomUUID();

            // when
            queue.softDelete(userId);

            // then
            assertThat(queue.getDeletedAt()).isNotNull();
            assertThat(queue.getDeletedBy()).isEqualTo(String.valueOf(userId));
        }
    }

    private Queue createQueue() {
        return Queue.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                100,
                LocalDateTime.now().plusHours(1),
                LocalDateTime.now().plusHours(2)
        );
    }

}