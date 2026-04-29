package org.ticketing.queue.presentation.dto.response;

import org.ticketing.queue.application.dto.result.UserStatusResult;

public record UserStatusResponse(
        Long rank,           // 현재 순번
        Long totalCount,     // 전체 대기 인원수
        boolean passed,
        String accessToken,  // 통과 시에만 값 존재
        String status        // WAITING | PASSED | EXPIRED
) {
    public static UserStatusResponse from(UserStatusResult result) {
        if (result.passed()) {
            return ofPassed(result.rank(), result.totalCount(), result.accessToken());
        }
        return ofWaiting(result.rank(), result.totalCount());
    }

    public static UserStatusResponse ofWaiting(Long rank, Long totalCount) {
        return new UserStatusResponse(rank, totalCount, false, null, "WAITING");
    }

    public static UserStatusResponse ofPassed(Long rank, Long totalCount, String accessToken) {
        return new UserStatusResponse(rank, totalCount, true, accessToken, "PASSED");
    }

    public static UserStatusResponse ofExpired() {
        return new UserStatusResponse(null, null, false, null, "EXPIRED");
    }}
