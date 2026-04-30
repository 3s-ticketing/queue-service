package org.ticketing.queue.domain.exception;

import org.springframework.http.HttpStatus;
import org.ticketing.common.exception.CustomException;

import java.util.UUID;

public class QueueNotFoundException extends CustomException {

    public QueueNotFoundException(UUID queueId) {
        super(String.format("해당 큐가 존재하지 않습니다. queueId = %s", queueId), HttpStatus.NOT_FOUND);
    }

    public QueueNotFoundException(String matchId) {
        super(String.format("해당 큐가 존재하지 않습니다. matchId = %s", matchId), HttpStatus.NOT_FOUND);
    }
}
