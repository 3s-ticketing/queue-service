package org.ticketing.queue.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.ticketing.common.domain.BaseEntity;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "p_queue")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Queue extends BaseEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "match_id", nullable = false)
    private UUID matchId;

    @Column(name = "max_active_users", nullable = false)
    private Integer maxActiveUsers;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "varchar(30) default 'CLOSED'")
    private QueueStatus status;

    @Column(name = "open_at", nullable = false)
    private LocalDateTime openAt;

    public static Queue create(UUID id, UUID matchId, int maxActiveUsers, QueueStatus status, LocalDateTime openAt) {
        return new Queue(
                id,
                matchId,
                maxActiveUsers,
                status,
                openAt
        );
    }

    public void update(UUID matchId, int maxActiveUsers, QueueStatus status, LocalDateTime openAt) {
        this.matchId = matchId;
        this.maxActiveUsers = maxActiveUsers;
        this.status = status;
        this.openAt = openAt;
    }

    public void softDelete(UUID userId) {
        delete(String.valueOf(userId));
    }

    public void activate(UUID updatedBy) {
        this.status = QueueStatus.ACTIVE;
    }

    public void close(UUID updatedBy) {
        this.status = QueueStatus.CLOSED;
    }

    public void ready(UUID updatedBy) {
        this.status = QueueStatus.READY;
    }

}