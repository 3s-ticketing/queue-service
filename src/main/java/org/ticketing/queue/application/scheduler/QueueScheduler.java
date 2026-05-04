package org.ticketing.queue.application.scheduler;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.ticketing.queue.domain.model.Queue;
import org.ticketing.queue.domain.model.QueueStatus;
import org.ticketing.queue.domain.repository.QueueRepository;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueueScheduler {

    private final QueueRepository queueRepository;

    /**
     * 매 30분: READY → ACTIVE 전환
     * openAt이 현재 시각 이전인 READY Queue 활성 처리
     */
    @Scheduled(cron = "0 */30 * * * *")
    @Transactional
    public void activateReadyQueues() {
        LocalDateTime now = LocalDateTime.now();
        log.info("[Queue 활성화 스케줄러] 실행 시각: {}", now);

        List<Queue> readyQueues = queueRepository.findAllReadyToActive(QueueStatus.READY, now);

        if (readyQueues.isEmpty()) {
            log.info("[Queue 활성화 스케줄러] 활성화 대상 Queue 없음");
            return;
        }

        log.info("[Queue 활성화 스케줄러] 활성화 대상 Queue 수: {}건", readyQueues.size());

        readyQueues.forEach(queue -> {
            try {
                queue.activate();
                log.debug("[Queue 활성화] queueId={}", queue.getId());
            } catch (Exception e) {
                log.error("[Queue 활성화 실패] queueId={}, reason={}", queue.getId(), e.getMessage());
            }
        });
    }

    /**
     * 매 30분: ACTIVE → EXPIRED 전환
     * expiredAt이 현재 시각 이전인 ACTIVE Queue 만료 처리
     */
    @Scheduled(cron = "0 */30 * * * *")
    @Transactional
    public void expireActiveQueues() {
        LocalDateTime now = LocalDateTime.now();
        log.info("[Queue 만료 스케줄러] 실행 시각: {}", now);

        List<Queue> activeQueues = queueRepository.findAllExpired(QueueStatus.ACTIVE, now);

        if (activeQueues.isEmpty()) {
            log.info("[Queue 만료 스케줄러] 만료 대상 Queue 없음");
            return;
        }

        log.info("[Queue 만료 스케줄러] 만료 대상 Queue 수: {}건", activeQueues.size());

        activeQueues.forEach(queue -> {
            try {
                queue.close();
                log.debug("[Queue 만료] queueId={}", queue.getId());
            } catch (Exception e) {
                log.error("[Queue 만료 실패] queueId={}, reason={}", queue.getId(), e.getMessage());
            }
        });
    }
}
