package org.ticketing.queue.presentation.dto.request;

import org.ticketing.queue.application.dto.command.TokenValidateCommand;

import java.util.UUID;

public record TokenValidateRequest(
        UUID matchId,
        UUID userId,
        String accessToken
) {
    public static TokenValidateCommand toCommand(TokenValidateRequest request) {
        return new TokenValidateCommand(
                request.matchId,
                request.userId,
                request.accessToken
        );
    }
}
