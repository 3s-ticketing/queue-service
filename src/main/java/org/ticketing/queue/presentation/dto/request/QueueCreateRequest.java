package org.ticketing.queue.presentation.dto.request;

import jakarta.validation.constraints.NotNull;
import org.ticketing.queue.application.dto.command.QueueCreateCommand;

import java.time.LocalDateTime;
import java.util.UUID;

public record QueueCreateRequest(
        @NotNull(message = "경기 아이디를 입력해주세요.")
        UUID matchId,
        @NotNull(message = "최대 활성 유저수를 입력해주세요.")
        Integer maxActiveUsers,
        @NotNull(message = "대기열 활성 일시를 입력해주세요.")
        LocalDateTime openAt
) {
    public static QueueCreateCommand toCommand(QueueCreateRequest request) {
        return new QueueCreateCommand(
                request.matchId,
                request.maxActiveUsers,
                request.openAt
        );
    }
}
