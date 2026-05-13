package org.ticketing.queue.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ticketing.queue.domain.exception.NotFoundClubMatchException;
import org.ticketing.queue.domain.exception.UnauthorizedClubAdminException;
import org.ticketing.queue.infrastructure.feign.ClubFeignClient;
import org.ticketing.queue.infrastructure.feign.MatchFeignClient;
import org.ticketing.queue.infrastructure.feign.response.MatchResponse;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchAuthorizationService {

    private final MatchFeignClient matchFeignClient;
    private final ClubFeignClient clubFeignClient;

    /**
     * CLUB_ADMIN이 해당 경기(홈/어웨이)의 클럽 관리자인지 검증
     */
    public void validateClubAdmin(UUID matchId, UUID requestUserId) {
        // 1. 경기 정보 조회 → 홈/어웨이 클럽 ID 추출
        MatchResponse match = fetchMatch(matchId);

        // 2. 홈/어웨이 클럽 관리자 ID 조회
        UUID homeAdminId = fetchAdminId(match.homeClubId());
        UUID awayAdminId = fetchAdminId(match.awayClubId());

        // 3. 요청자가 둘 중 하나의 관리자인지 확인
        boolean isAuthorized = requestUserId.equals(homeAdminId)
                            || requestUserId.equals(awayAdminId);

        if (!isAuthorized) {
            log.warn("[Auth] CLUB_ADMIN 권한 없음. matchId={}, requestUserId={}", matchId, requestUserId);
            throw new UnauthorizedClubAdminException(matchId, requestUserId);
        }

        log.info("[Auth] CLUB_ADMIN 검증 완료. matchId={}, requestUserId={}", matchId, requestUserId);
    }

    private MatchResponse fetchMatch(UUID matchId) {
        try {
            return matchFeignClient.getMatch(matchId);
        } catch (Exception e) {
            log.error("[Auth] 경기 정보 조회 실패. matchId={}", matchId, e);
            throw new NotFoundClubMatchException(matchId, null);
        }
    }

    private UUID fetchAdminId(UUID clubId) {
        try {
            return clubFeignClient.getClub(clubId).adminId();
        } catch (Exception e) {
            log.error("[Auth] 클럽 정보 조회 실패. clubId={}", clubId, e);
            throw new NotFoundClubMatchException(null, clubId);
        }
    }
}