package io.github.team404.tikitaka.booking.repository;

import static io.github.team404.tikitaka.booking.entity.QReservation.reservation;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import io.github.team404.tikitaka.booking.entity.Reservation;
import io.github.team404.tikitaka.booking.entity.ReservationStatus;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;

public class ReservationRepositoryImpl implements ReservationRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    // @DataJpaTest 등 Repository 슬라이스 테스트는 일반 @Configuration 빈을 로드하지 않으므로,
    // 공용 JPAQueryFactory 빈을 주입받지 않고 슬라이스에서도 항상 제공되는 EntityManager로 직접 생성한다
    public ReservationRepositoryImpl(EntityManager entityManager) {
        this.queryFactory = new JPAQueryFactory(entityManager);
    }

    @Override
    public Page<Reservation> search(ReservationSearchCondition condition, Pageable pageable) {
        List<Reservation> content = queryFactory
                .selectFrom(reservation)
                .where(
                        userIdEq(condition.userId()),
                        simulationIdEq(condition.simulationId()),
                        scheduleIdEq(condition.scheduleId()),
                        statusEq(condition.status()),
                        createdAtGoe(condition.createdFrom()),
                        createdAtLoe(condition.createdTo())
                )
                .orderBy(reservation.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        return PageableExecutionUtils.getPage(content, pageable, () -> queryFactory
                .select(reservation.count())
                .from(reservation)
                .where(
                        userIdEq(condition.userId()),
                        simulationIdEq(condition.simulationId()),
                        scheduleIdEq(condition.scheduleId()),
                        statusEq(condition.status()),
                        createdAtGoe(condition.createdFrom()),
                        createdAtLoe(condition.createdTo())
                )
                .fetchOne());
    }

    private BooleanExpression userIdEq(Long userId) {
        return userId != null ? reservation.userId.eq(userId) : null;
    }

    private BooleanExpression simulationIdEq(Long simulationId) {
        return simulationId != null ? reservation.simulationId.eq(simulationId) : null;
    }

    private BooleanExpression scheduleIdEq(Long scheduleId) {
        return scheduleId != null ? reservation.scheduleId.eq(scheduleId) : null;
    }

    private BooleanExpression statusEq(ReservationStatus status) {
        return status != null ? reservation.status.eq(status) : null;
    }

    private BooleanExpression createdAtGoe(LocalDateTime createdFrom) {
        return createdFrom != null ? reservation.createdAt.goe(createdFrom) : null;
    }

    private BooleanExpression createdAtLoe(LocalDateTime createdTo) {
        return createdTo != null ? reservation.createdAt.loe(createdTo) : null;
    }
}