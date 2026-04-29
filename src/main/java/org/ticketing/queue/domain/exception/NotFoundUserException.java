package org.ticketing.queue.domain.exception;

import org.springframework.http.HttpStatus;
import org.ticketing.common.exception.CustomException;

import java.util.UUID;

public class NotFoundUserException extends CustomException {

    public NotFoundUserException(UUID matchId, UUID userId) {
        super(String.format("대기열에 존재하지 않는 유저입니다. matchId=%s, userId=%s",
                matchId, userId), HttpStatus.NOT_FOUND);
    }
}
