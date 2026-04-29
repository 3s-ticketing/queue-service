package org.ticketing.queue.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.ticketing.queue.domain.model.QueueExitReason;
import org.ticketing.queue.domain.model.QueueHistory;
import org.ticketing.queue.domain.repository.QueueHistoryRepository;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class QueueHistoryService {

    private final QueueHistoryRepository queueHistoryRepository;

    @Async
    public void record(UUID matchId, UUID userId, LocalDateTime enteredAt, QueueExitReason exitReason) {
        QueueHistory history = switch (exitReason) {
            case PASSED           -> QueueHistory.ofPassed(matchId, userId, enteredAt);
            case IO_ERROR         -> QueueHistory.ofIoError(matchId, userId, enteredAt);
            case UNEXPECTED_ERROR -> QueueHistory.ofUnexpectedError(matchId, userId, enteredAt);
            case TIMEOUT          -> QueueHistory.ofTimeout(matchId, userId, enteredAt);
            case REFRESH          -> QueueHistory.ofRefresh(matchId, userId, enteredAt);
            case BANNED           -> QueueHistory.ofBanned(matchId, userId, enteredAt);
        };

        try {
            queueHistoryRepository.save(history);
        } catch (Exception e) {
            log.warn("[QueueHistory] 저장 실패. matchId={}, userId={}, reason={}",
                    history.getMatchId(), history.getUserId(), history.getExitReason(), e);
        }
    }
}