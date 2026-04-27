package org.ticketing.queue.application.dto.command;

import java.util.UUID;

public record TokenValidateCommand(
        UUID matchId,
        UUID userId,
        String accessToken
) {
}
