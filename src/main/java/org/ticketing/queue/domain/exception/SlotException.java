package org.ticketing.queue.domain.exception;

import org.springframework.http.HttpStatus;
import org.ticketing.common.exception.CustomException;

public class SlotException extends CustomException {

    public SlotException(String message, HttpStatus status) {
        super(message, status);
    }
}
