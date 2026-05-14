package org.ticketing.queue.infrastructure.feign.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record MatchResponse(
        UUID id,

        @JsonProperty("home_club_id")
        UUID homeClubId,

        @JsonProperty("away_club_id")
        UUID awayClubId
) {
}