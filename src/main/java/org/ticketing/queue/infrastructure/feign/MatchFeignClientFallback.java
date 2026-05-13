package org.ticketing.queue.infrastructure.feign;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ticketing.queue.domain.exception.NotFoundClubMatchException;
import org.ticketing.queue.infrastructure.feign.response.MatchResponse;

import java.util.UUID;

@Slf4j
@Component
public class MatchFeignClientFallback implements MatchFeignClient {

    @Override
    public MatchResponse getMatch(UUID matchId, String internalService) {
        log.error("[Feign Fallback] match-service 호출 실패. matchId={}, service={}", matchId, internalService);
        throw new NotFoundClubMatchException(matchId, null);
    }
}