package io.github.team404.tikitaka.performanceseat.repository;

import static io.github.team404.tikitaka.performanceseat.entity.QPerformance.performance;
import static io.github.team404.tikitaka.performanceseat.entity.QPerformanceSchedule.performanceSchedule;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import io.github.team404.tikitaka.performanceseat.entity.Performance;
import io.github.team404.tikitaka.performanceseat.entity.PerformanceGenre;
import io.github.team404.tikitaka.performanceseat.entity.PerformanceRegion;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;

public class PerformanceRepositoryImpl implements PerformanceRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    // @DataJpaTest 등 Repository 슬라이스 테스트는 일반 @Configuration 빈을 로드하지 않으므로,
    // 공용 JPAQueryFactory 빈 대신 슬라이스에서도 항상 제공되는 EntityManager로 직접 생성한다 (Issue #69 결정 참고)
    public PerformanceRepositoryImpl(EntityManager entityManager) {
        this.queryFactory = new JPAQueryFactory(entityManager);
    }

    @Override
    public Page<Performance> search(PerformanceSearchCondition condition, Pageable pageable) {
        List<Performance> content = queryFactory
                .selectFrom(performance)
                .where(
                        genreEq(condition.genre()),
                        regionEq(condition.region()),
                        scheduleDateInRange(condition.performanceDateFrom(), condition.performanceDateTo()))
                .orderBy(performance.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        return PageableExecutionUtils.getPage(content, pageable, () -> queryFactory
                .select(performance.count())
                .from(performance)
                .where(
                        genreEq(condition.genre()),
                        regionEq(condition.region()),
                        scheduleDateInRange(condition.performanceDateFrom(), condition.performanceDateTo()))
                .fetchOne());
    }

    @Override
    public Page<Performance> searchByKeyword(
            String keyword, PerformanceGenre genre, PerformanceRegion region, Pageable pageable) {
        List<Performance> content = queryFactory
                .selectFrom(performance)
                .where(keywordMatches(keyword), genreEq(genre), regionEq(region))
                .orderBy(performance.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        return PageableExecutionUtils.getPage(content, pageable, () -> queryFactory
                .select(performance.count())
                .from(performance)
                .where(keywordMatches(keyword), genreEq(genre), regionEq(region))
                .fetchOne());
    }

    // 형태소 분석 없는 단순 부분일치 — 폴백 상태에서만 쓰인다 (#93).
    private BooleanExpression keywordMatches(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return performance.title.containsIgnoreCase(keyword)
                .or(performance.artist.containsIgnoreCase(keyword));
    }

    private BooleanExpression genreEq(PerformanceGenre genre) {
        return genre != null ? performance.genre.eq(genre) : null;
    }

    private BooleanExpression regionEq(PerformanceRegion region) {
        return region != null ? performance.region.eq(region) : null;
    }

    // 날짜는 Performance가 아니라 PerformanceSchedule.performanceDatetime 소관이라 EXISTS 서브쿼리로 평가한다.
    // 명시적 join은 회차가 여러 개인 공연이 중복 반환되는 문제가 있는데, EXISTS는 driving table이 Performance
    // 그대로라 다중 회차 공연도 항상 한 번만 반환된다 (다중 회차 중복 반환 정책 결정, Issue #71).
    private BooleanExpression scheduleDateInRange(LocalDateTime from, LocalDateTime to) {
        BooleanExpression dateCondition = combine(scheduleDatetimeGoe(from), scheduleDatetimeLoe(to));
        if (dateCondition == null) {
            return null;
        }
        return JPAExpressions.selectOne()
                .from(performanceSchedule)
                .where(performanceSchedule.performanceId.eq(performance.id), dateCondition)
                .exists();
    }

    private BooleanExpression scheduleDatetimeGoe(LocalDateTime from) {
        return from != null ? performanceSchedule.performanceDatetime.goe(from) : null;
    }

    private BooleanExpression scheduleDatetimeLoe(LocalDateTime to) {
        return to != null ? performanceSchedule.performanceDatetime.loe(to) : null;
    }

    private BooleanExpression combine(BooleanExpression left, BooleanExpression right) {
        if (left == null) {
            return right;
        }
        return right == null ? left : left.and(right);
    }
}
