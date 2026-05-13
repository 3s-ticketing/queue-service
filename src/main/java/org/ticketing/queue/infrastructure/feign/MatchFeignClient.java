package org.ticketing.queue.infrastructure.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.ticketing.queue.infrastructure.feign.response.MatchResponse;

import java.util.UUID;

@FeignClient(name = "match-service")
public interface MatchFeignClient {

    @GetMapping("/api/matches/{matchId}")
    MatchResponse getMatch(@PathVariable("matchId") UUID matchId);
}