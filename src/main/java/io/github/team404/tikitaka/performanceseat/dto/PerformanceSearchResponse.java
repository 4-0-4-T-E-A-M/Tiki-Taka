package io.github.team404.tikitaka.performanceseat.dto;

import java.util.List;
import org.springframework.data.domain.Page;

// 검색 결과 응답. Spring Data Page를 그대로 직렬화하면 JSON 구조가 불안정하다는 경고가 있어
// 필요한 필드만 고정된 형태로 노출한다. QueryDSL 경로/ES 경로가 같은 형태를 유지해야 한다
// (docs/tradeoffs/search/queryDSL-vs-es-role-split.md).
public record PerformanceSearchResponse(
        List<PerformanceResponse> content,
        long totalElements,
        int page,
        int size
) {
    public static PerformanceSearchResponse from(Page<PerformanceResponse> page) {
        return new PerformanceSearchResponse(
                page.getContent(),
                page.getTotalElements(),
                page.getNumber(),
                page.getSize());
    }
}
