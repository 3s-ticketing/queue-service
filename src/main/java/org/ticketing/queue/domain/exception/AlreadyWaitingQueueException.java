package org.ticketing.queue.domain.exception;

import org.springframework.http.HttpStatus;
import org.ticketing.common.exception.CustomException;

import java.util.UUID;

public class AlreadyWaitingQueueException extends CustomException {

    public AlreadyWaitingQueueException(UUID matchId, UUID userId) {
        super(String.format("이미 대기열에서 대기중입니다. matchId = %s, userId = %s",
                matchId, userId), HttpStatus.BAD_REQUEST);
    }
}
