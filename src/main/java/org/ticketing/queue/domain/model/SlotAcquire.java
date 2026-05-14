package org.ticketing.queue.domain.model;

import java.time.LocalDateTime;

public record SlotAcquire(AcquireResult status, LocalDateTime enteredAt) {

    public static SlotAcquire of(AcquireResult status) {
        return new SlotAcquire(status, null);
    }

    public static SlotAcquire success(LocalDateTime enteredAt) {
        return new SlotAcquire(AcquireResult.SUCCESS, enteredAt);
    }
}