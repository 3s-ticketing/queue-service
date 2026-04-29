package org.ticketing.queue.application.dto.result;

public record UserStatusResult(
        Long rank,           // 현재 순번
        Long totalCount,     // 전체 대기 인원수
        boolean passed,
        String accessToken   // 통과 시에만 값 존재
) {
    public static UserStatusResult of(Long rank, Long totalCount) {
        return new UserStatusResult(rank, totalCount, false, null);
    }

    public static UserStatusResult ofWaiting(Long rank, Long totalCount) {
        return new UserStatusResult(rank, totalCount, false, null);
    }

    public static UserStatusResult ofPassed(Long rank, Long totalCount, String accessToken) {
        return new UserStatusResult(rank, totalCount, true, accessToken);
    }
}
