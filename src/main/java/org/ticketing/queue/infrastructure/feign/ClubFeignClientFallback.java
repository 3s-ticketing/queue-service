package org.ticketing.queue.infrastructure.feign;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ticketing.queue.domain.exception.NotFoundClubMatchException;
import org.ticketing.queue.infrastructure.feign.response.ClubResponse;

import java.util.UUID;

@Slf4j
@Component
public class ClubFeignClientFallback implements ClubFeignClient {

    @Override
    public ClubResponse getClub(UUID clubId, String internalService) {
        log.error("[Feign Fallback] club-service 호출 실패. clubId={}, service={}", clubId, internalService);
        throw new NotFoundClubMatchException(null, clubId);
    }
}
