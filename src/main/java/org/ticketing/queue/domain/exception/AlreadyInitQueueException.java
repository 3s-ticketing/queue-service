package org.ticketing.queue.domain.exception;

import org.springframework.http.HttpStatus;
import org.ticketing.common.exception.CustomException;

import java.util.UUID;

public class AlreadyInitQueueException extends CustomException {

    public AlreadyInitQueueException(UUID matchId) {
        super(String.format("이미 해당 경기의 큐 설정이 존재합니다. matchId = %s", matchId), HttpStatus.BAD_REQUEST);
    }
}
