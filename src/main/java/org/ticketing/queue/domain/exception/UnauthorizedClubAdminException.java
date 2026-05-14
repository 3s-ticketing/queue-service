package org.ticketing.queue.domain.exception;

import org.springframework.http.HttpStatus;
import org.ticketing.common.exception.CustomException;

import java.util.UUID;

public class UnauthorizedClubAdminException extends CustomException {

    public UnauthorizedClubAdminException(UUID matchId, UUID userId) {
        super(String.format("해당 경기의 클럽 관리자가 아닙니다. matchId=%s, userId=%s", matchId, userId), HttpStatus.UNAUTHORIZED);
    }
}