package io.github.team404.tikitaka.performanceseat.repository;

import io.github.team404.tikitaka.performanceseat.entity.PerformanceGenre;
import io.github.team404.tikitaka.performanceseat.entity.PerformanceRegion;
import java.time.LocalDateTime;

// 공연 복합 검색 조건 - 모든 필드는 선택(null 허용)이며 null인 조건은 무시된다.
// performanceDateFrom/To는 Performance가 아니라 PerformanceSchedule.performanceDatetime 기준이다.
public record PerformanceSearchCondition(
        PerformanceGenre genre,
        PerformanceRegion region,
        LocalDateTime performanceDateFrom,
        LocalDateTime performanceDateTo
) {
}
