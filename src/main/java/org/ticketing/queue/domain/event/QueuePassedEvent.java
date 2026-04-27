//package org.ticketing.queue.domain.event;
//
//import lombok.Getter;
//
//import java.time.LocalDateTime;
//import java.util.UUID;
//
//@Getter
//public class QueuePassedEvent {
//
//    private final UUID matchId;
//    private final UUID userId;
//    private final String accessToken;   // 발급된 JWT 토큰
//    private final LocalDateTime passedAt;
//
//    private QueuePassedEvent(UUID matchId, UUID userId, String accessToken, LocalDateTime passedAt) {
//        this.matchId = matchId;
//        this.userId = userId;
//        this.accessToken = accessToken;
//        this.passedAt = passedAt;
//    }
//
//    public static QueuePassedEvent of(UUID matchId, UUID userId, String accessToken) {
//        return new QueuePassedEvent(matchId, userId, accessToken, LocalDateTime.now());
//    }
//}
