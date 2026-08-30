package io.github.team404.tikitaka.performanceseat.repository;

import io.github.team404.tikitaka.performanceseat.entity.Performance;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PerformanceRepositoryCustom {

    // genre/region/공연일시 기간을 선택적으로 조합해 조회한다 (조건이 전부 null이면 전체 조회).
    // 회차(PerformanceSchedule) 조건은 EXISTS 서브쿼리로 평가되어 다중 회차 공연도 한 번만 반환된다.
    Page<Performance> search(PerformanceSearchCondition condition, Pageable pageable);
}
