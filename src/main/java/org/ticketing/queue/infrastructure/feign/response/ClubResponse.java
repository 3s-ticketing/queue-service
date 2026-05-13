package org.ticketing.queue.infrastructure.feign.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record ClubResponse(
        @JsonProperty("club_id")
        UUID clubId,

        @JsonProperty("admin_id")
        UUID adminId
) {
}