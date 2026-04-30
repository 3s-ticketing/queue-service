package org.ticketing.queue.domain.model;

public enum QueueExitReason {
    PASSED,
    IO_ERROR,
    UNEXPECTED_ERROR,
    TIMEOUT,
    REFRESH,
    BANNED
}
