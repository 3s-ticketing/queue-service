package org.ticketing.queue.presentation.dto.request;

import jakarta.validation.constraints.NotNull;
import org.ticketing.queue.application.dto.command.TokenValidateCommand;

import java.util.UUID;

public record TokenValidateRequest(
        @NotNull(message = "경기 아이디를 입력해주세요.")
        UUID matchId,
        @NotNull(message = "유저 아이디를 입력해주세요.")
        UUID userId,
        @NotNull(message = "토큰을 입력해주세요.")
        String accessToken
) {
    public static TokenValidateCommand toCommand(TokenValidateRequest request, UUID matchId) {
        return new TokenValidateCommand(
                matchId,
                request.userId,
                request.accessToken
        );
    }
}
