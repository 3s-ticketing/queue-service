package org.ticketing.queue.domain.exception;

import org.springframework.http.HttpStatus;
import org.ticketing.common.exception.CustomException;

import java.util.UUID;

public class BannedUserException extends CustomException {

    public BannedUserException(UUID matchId, UUID userId) {
        super(String.format("차단된 유저입니다. matchId=%s, userId=%s", matchId, userId), HttpStatus.FORBIDDEN);
    }
}