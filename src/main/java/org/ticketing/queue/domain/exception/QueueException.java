package org.ticketing.queue.domain.exception;

import org.iimsa.common.exception.CustomException;
import org.springframework.http.HttpStatus;

public class QueueException extends CustomException {

    public QueueException(String message, HttpStatus status) {
        super(message, status);
    }

    public QueueException(String field, String message, HttpStatus status) {
        super(field, message, status);
    }
}
