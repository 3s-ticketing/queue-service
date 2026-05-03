package org.ticketing.queue.domain.exception;

import org.springframework.http.HttpStatus;
import org.ticketing.common.exception.CustomException;

import java.util.UUID;

public class QueueNotOpenException extends CustomException {

    public QueueNotOpenException(UUID matchId) {
        super(String.format("예매 시작 시간이 아닙니다. matchId = %s", matchId), HttpStatus.BAD_REQUEST);
    }
}
