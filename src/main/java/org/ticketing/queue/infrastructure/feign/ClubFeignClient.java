package org.ticketing.queue.infrastructure.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.ticketing.queue.infrastructure.feign.response.ClubResponse;

import java.util.UUID;

@FeignClient(name = "club-service")
public interface ClubFeignClient {

    @GetMapping("/internal/clubs/{clubId}")
    ClubResponse getClub(@PathVariable("clubId") UUID clubId);
}