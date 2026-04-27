package org.ticketing.queue.infrastructure.persistence;

import com.querydsl.core.types.dsl.BooleanExpression;
import org.ticketing.queue.domain.model.QQueue;
import org.ticketing.queue.domain.model.QueueStatus;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.Function;

public class QueueSpecification {

    public static Function<QQueue, BooleanExpression> matchIdEq(UUID matchId) {
        return q -> matchId != null ? q.matchId.eq(matchId) : null;
    }

    public static Function<QQueue, BooleanExpression> maxActiveUsersEq(Integer maxActiveUsers) {
        return q -> maxActiveUsers != null ? q.maxActiveUsers.eq(maxActiveUsers) : null;
    }

    public static Function<QQueue, BooleanExpression> statusEq(QueueStatus status) {
        return q -> status != null ? q.status.eq(status) : null;
    }

    public static Function<QQueue, BooleanExpression> openAtGoe(LocalDateTime openAtFrom) {
        return q -> openAtFrom != null ? q.openAt.goe(openAtFrom) : null;
    }

    public static Function<QQueue, BooleanExpression> openAtLoe(LocalDateTime openAtTo) {
        return q -> openAtTo != null ? q.openAt.loe(openAtTo) : null;
    }

    public static Function<QQueue, BooleanExpression> isDeleted(Boolean isDeleted) {
        return q -> isDeleted != null ?
                (isDeleted ? q.deletedAt.isNotNull() : q.deletedAt.isNull())
                : null;
    }
}
