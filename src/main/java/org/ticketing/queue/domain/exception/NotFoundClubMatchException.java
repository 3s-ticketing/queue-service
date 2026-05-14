package org.ticketing.queue.domain.exception;

import org.springframework.http.HttpStatus;
import org.ticketing.common.exception.CustomException;

import java.util.UUID;

public class NotFoundClubMatchException extends CustomException {

    public NotFoundClubMatchException(UUID matchId, UUID clubId) {
        super(String.format("해당 경기/클럽 조회 실패. matchId=%s, clubId=%s", matchId, clubId), HttpStatus.NOT_FOUND);
    }
}
