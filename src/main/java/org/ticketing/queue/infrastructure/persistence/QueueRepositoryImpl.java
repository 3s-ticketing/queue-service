package org.ticketing.queue.infrastructure.persistence;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.ticketing.queue.domain.dto.QueueProjection;
import org.ticketing.queue.domain.dto.QueueSearchCondition;
import org.ticketing.queue.domain.exception.QueueNotFoundException;
import org.ticketing.queue.domain.model.Queue;
import org.ticketing.queue.domain.repository.QueueRepository;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

import static org.ticketing.queue.domain.model.QQueue.queue;

@Repository
@RequiredArgsConstructor
public class QueueRepositoryImpl implements QueueRepository {

    private final JpaQueueRepository jpaQueueRepository;
    private final JPAQueryFactory queryFactory;

    @Override
    public Queue findById(UUID queueId) {
        return jpaQueueRepository.findById(queueId)
                .orElseThrow(() -> new QueueNotFoundException(queueId));
    }

    @Override
    public Queue findByMatchId(UUID matchId) {
        return jpaQueueRepository.findByMatchId(matchId)
                .orElseThrow(() -> new QueueNotFoundException(String.valueOf(matchId)));
    }

    @Override
    public Queue findByIdAndDeletedAtIsNull(UUID queueId) {
        return jpaQueueRepository.findByIdAndDeletedAtIsNull(queueId)
                .orElseThrow(() -> new QueueNotFoundException(queueId));
    }

    @Override
    public Page<QueueProjection> findAllByCondition(QueueSearchCondition cond, Pageable pageable){
        BooleanExpression where = Stream.of(
                        QueueSpecification.matchIdEq(cond.matchId()),
                        QueueSpecification.maxActiveUsersEq(cond.maxActiveUsers()),
                        QueueSpecification.statusEq(cond.status()),
                        QueueSpecification.openAtGoe(cond.openAtFrom()),
                        QueueSpecification.openAtLoe(cond.openAtTo()),
                        QueueSpecification.isDeleted(cond.isDeleted())
                )
                .map(fn -> fn.apply(queue))
                .filter(Objects::nonNull)
                .reduce(BooleanExpression::and)
                .orElse(null);

        List<QueueProjection> content = queryFactory
                .select(Projections.constructor(QueueProjection.class,
                        queue.id,
                        queue.matchId,
                        queue.maxActiveUsers,
                        queue.status,
                        queue.openAt
                ))
                .from(queue)
                .where(where)
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(getOrderSpecifiers(pageable.getSort()))
                .fetch();

        Long total = queryFactory
                .select(queue.count())
                .from(queue)
                .where(where)
                .fetchOne();

        return new PageImpl<>(content, pageable, total);
    }

    @Override
    public Queue save(Queue queue) {
        return jpaQueueRepository.save(queue);
    }

    @Override
    public boolean existsByMatchId(UUID matchId) {
        return jpaQueueRepository.existsByMatchId(matchId);
    }

    private OrderSpecifier<?>[] getOrderSpecifiers(Sort sort) {

        if (sort == null || sort.isUnsorted()) {
            return new OrderSpecifier[]{queue.createdAt.desc(), queue.modifiedAt.desc()};
        }

        return sort.stream()
                .map(order -> {
                    boolean asc = order.isAscending();

                    return switch (order.getProperty()) {
                        case "createdAt" ->
                                asc ? queue.createdAt.asc()
                                        : queue.createdAt.desc();

                        case "modifiedAt" ->
                                asc ? queue.modifiedAt.asc()
                                        : queue.modifiedAt.desc();

                        case "matchId" ->
                                asc ? queue.matchId.asc()
                                        : queue.matchId.desc();

                        case "maxActiveUsers" ->
                                asc ? queue.maxActiveUsers.asc()
                                        : queue.maxActiveUsers.desc();

                        case "status" ->
                                asc ? queue.status.asc()
                                        : queue.status.desc();

                        case "openAt" ->
                                asc ? queue.openAt.asc()
                                        : queue.openAt.desc();

                        default ->
                                queue.createdAt.desc();
                    };
                })
                .toArray(OrderSpecifier[]::new);
    }
}
