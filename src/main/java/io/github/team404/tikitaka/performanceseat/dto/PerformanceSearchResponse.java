package io.github.team404.tikitaka.performanceseat.dto;

import java.util.List;
import org.springframework.data.domain.Page;

// 검색 결과 응답. Spring Data Page를 그대로 직렬화하면 JSON 구조가 불안정하다는 경고가 있어
// 필요한 필드만 고정된 형태로 노출한다. QueryDSL 경로/ES 경로가 같은 형태를 유지해야 한다
// (docs/tradeoffs/search/queryDSL-vs-es-role-split.md).
//
// degraded=true 는 Elasticsearch 장애로 PostgreSQL 폴백이 사용됐다는 뜻 — 형태소 분석·관련도
// 정렬·자동완성 없이 단순 부분일치 + 최신순으로 축소된 결과다 (#93). 페이지네이션 계약은 동일하다.
public record PerformanceSearchResponse(
        List<PerformanceResponse> content,
        long totalElements,
        int page,
        int size,
        boolean degraded
) {
    public static PerformanceSearchResponse of(Page<PerformanceResponse> page, boolean degraded) {
        return new PerformanceSearchResponse(
                page.getContent(),
                page.getTotalElements(),
                page.getNumber(),
                page.getSize(),
                degraded);
    }
}
