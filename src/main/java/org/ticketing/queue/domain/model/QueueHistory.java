package org.ticketing.queue.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "p_queue_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class QueueHistory {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "match_id", nullable = false)
    private UUID matchId;

    @Column(name = "entered_at", nullable = false)
    private LocalDateTime enteredAt;

    @Column(name = "exited_at", nullable = false)
    private LocalDateTime exitedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private QueueHistoryStatus status;

    // Factory Methods
    public static QueueHistory create(UUID userId, UUID matchId, LocalDateTime enteredAt, QueueHistoryStatus status) {
        return new QueueHistory(
                UUID.randomUUID(),
                userId,
                matchId,
                enteredAt,
                LocalDateTime.now(),
                status
        );
    }

    // Business Methods
    public void exit(LocalDateTime exitedAt, QueueHistoryStatus status) {
        this.exitedAt = exitedAt;
        this.status = status;
    }
}