package org.ticketing.queue.domain.exception;

import org.springframework.http.HttpStatus;
import org.ticketing.common.exception.CustomException;

public class TokenException extends CustomException {

    public TokenException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
