package org.ticketing.queue.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.ticketing.common.domain.BaseEntity;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Entity
@Table(name = "p_banned_users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class BannedUser extends BaseEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "match_id", nullable = false)
    private UUID matchId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "banned_at", nullable = false)
    private LocalDateTime bannedAt;

    public static BannedUser create(UUID matchId, UUID userId) {
        return new BannedUser(
                UUID.randomUUID(),
                matchId,
                userId,
                LocalDateTime.now()
        );
    }
}
