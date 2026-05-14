package org.ticketing.queue.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ticketing.queue.domain.exception.UnauthorizedClubAdminException;
import org.ticketing.queue.infrastructure.feign.ClubFeignClient;
import org.ticketing.queue.infrastructure.feign.MatchFeignClient;
import org.ticketing.queue.infrastructure.feign.response.MatchResponse;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchAuthorizationService {

    private final MatchFeignClient matchFeignClient;
    private final ClubFeignClient clubFeignClient;

    private static final String SERVICE_NAME = "queue-service";

    public void validateClubAdmin(UUID matchId, UUID requestUserId) {
        // 1. 경기 정보 조회
        MatchResponse match = matchFeignClient.getMatch(matchId, SERVICE_NAME);

        // 2. 홈/어웨이 클럽 관리자 ID 병렬 조회
        CompletableFuture<UUID> homeFuture = CompletableFuture.supplyAsync(() ->
                clubFeignClient.getClub(match.homeClubId(), SERVICE_NAME).adminId()
        );
        CompletableFuture<UUID> awayFuture = CompletableFuture.supplyAsync(() ->
                clubFeignClient.getClub(match.awayClubId(), SERVICE_NAME).adminId()
        );

        UUID homeAdminId = homeFuture.join();
        UUID awayAdminId = awayFuture.join();

        // 3. 권한 검증
        boolean isAuthorized = requestUserId.equals(homeAdminId)
                || requestUserId.equals(awayAdminId);

        if (!isAuthorized) {
            log.warn("[Auth] CLUB_ADMIN 권한 없음. matchId={}, requestUserId={}", matchId, requestUserId);
            throw new UnauthorizedClubAdminException(matchId, requestUserId);
        }

        log.info("[Auth] CLUB_ADMIN 검증 완료. matchId={}, requestUserId={}", matchId, requestUserId);
    }
}