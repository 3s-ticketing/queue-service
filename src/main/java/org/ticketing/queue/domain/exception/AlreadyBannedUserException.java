package org.ticketing.queue.domain.exception;

import org.springframework.http.HttpStatus;
import org.ticketing.common.exception.CustomException;

import java.util.UUID;

public class AlreadyBannedUserException extends CustomException {

    public AlreadyBannedUserException(UUID matchId, UUID userId) {
        super(String.format("이미 차단된 유저입니다. matchId = %s, userId = %s", matchId, userId), HttpStatus.BAD_REQUEST);
    }
}