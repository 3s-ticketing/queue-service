package org.ticketing.queue.domain.exception;

import org.springframework.http.HttpStatus;
import org.ticketing.common.exception.CustomException;

public class QueueException extends CustomException {

    public QueueException(String message, HttpStatus status) {
        super(message, status);
    }
}
