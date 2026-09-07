package io.github.team404.tikitaka.performanceseat.repository;

import io.github.team404.tikitaka.performanceseat.entity.Performance;
import io.github.team404.tikitaka.performanceseat.entity.PerformanceGenre;
import io.github.team404.tikitaka.performanceseat.entity.PerformanceRegion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PerformanceRepositoryCustom {

    // genre/region/공연일시 기간을 선택적으로 조합해 조회한다 (조건이 전부 null이면 전체 조회).
    // 회차(PerformanceSchedule) 조건은 EXISTS 서브쿼리로 평가되어 다중 회차 공연도 한 번만 반환된다.
    Page<Performance> search(PerformanceSearchCondition condition, Pageable pageable);

    // Elasticsearch 장애 시 폴백 경로 (#93). 키워드는 title/artist에 대한 대소문자 무시 부분일치로만
    // 매칭하고(형태소 분석·관련도 정렬 없음), 정렬은 최신순으로 축소한다
    // (docs/tradeoffs/search/queryDSL-vs-es-role-split.md의 폴백 정책).
    Page<Performance> searchByKeyword(
            String keyword, PerformanceGenre genre, PerformanceRegion region, Pageable pageable);
}
