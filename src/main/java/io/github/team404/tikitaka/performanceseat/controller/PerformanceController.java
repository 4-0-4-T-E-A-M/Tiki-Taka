package io.github.team404.tikitaka.performanceseat.controller;

import io.github.team404.tikitaka.global.response.BaseResponse;
import io.github.team404.tikitaka.performanceseat.dto.PerformanceCreateRequest;
import io.github.team404.tikitaka.performanceseat.dto.PerformanceDetailResponse;
import io.github.team404.tikitaka.performanceseat.dto.PerformanceResponse;
import io.github.team404.tikitaka.performanceseat.dto.PerformanceSearchResponse;
import io.github.team404.tikitaka.performanceseat.dto.PerformanceUpdateRequest;
import io.github.team404.tikitaka.performanceseat.entity.PerformanceGenre;
import io.github.team404.tikitaka.performanceseat.entity.PerformanceRegion;
import io.github.team404.tikitaka.performanceseat.search.PerformanceSearchService;
import io.github.team404.tikitaka.performanceseat.service.PerformanceService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/performances")
@RequiredArgsConstructor
public class PerformanceController {

    private final PerformanceService performanceService;
    private final PerformanceSearchService performanceSearchService;

    // 키워드(q) 기반 공연 검색 — 기본 Elasticsearch(nori), 장애 시 PostgreSQL 폴백(degraded=true).
    // genre/region은 선택 필터. 구조화 필터만 쓰는 조회는 QueryDSL 경로 소관(#86 역할 분리).
    @GetMapping("/search")
    public ResponseEntity<BaseResponse<PerformanceSearchResponse>> search(
            @RequestParam(name = "q", required = false) String keyword,
            @RequestParam(required = false) PerformanceGenre genre,
            @RequestParam(required = false) PerformanceRegion region,
            @PageableDefault(size = 20) Pageable pageable) {
        PerformanceSearchResponse response =
                performanceSearchService.search(keyword, genre, region, pageable);
        return ResponseEntity.ok(BaseResponse.success("공연 검색에 성공했습니다.", response));
    }

    @PostMapping
    public ResponseEntity<BaseResponse<PerformanceResponse>> create(@RequestBody PerformanceCreateRequest request) {
        PerformanceResponse response = PerformanceResponse.from(performanceService.createPerformance(request));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.success("공연 생성에 성공했습니다.", response));
    }

    @PutMapping("/{performanceId}")
    public ResponseEntity<BaseResponse<PerformanceResponse>> update(
            @PathVariable Long performanceId, @RequestBody PerformanceUpdateRequest request) {
        PerformanceResponse response = PerformanceResponse.from(
                performanceService.updatePerformance(performanceId, request));
        return ResponseEntity.ok(BaseResponse.success("공연 수정에 성공했습니다.", response));
    }

    @DeleteMapping("/{performanceId}")
    public ResponseEntity<BaseResponse<Void>> delete(@PathVariable Long performanceId) {
        performanceService.deletePerformance(performanceId);
        return ResponseEntity.ok(BaseResponse.success("공연 삭제에 성공했습니다."));
    }

    @GetMapping
    public ResponseEntity<BaseResponse<List<PerformanceResponse>>> list() {
        List<PerformanceResponse> responses = performanceService.listPerformances().stream()
                .map(PerformanceResponse::from)
                .toList();
        return ResponseEntity.ok(BaseResponse.success("공연 목록 조회에 성공했습니다.", responses));
    }

    @GetMapping("/{performanceId}")
    public ResponseEntity<BaseResponse<PerformanceDetailResponse>> get(@PathVariable Long performanceId) {
        PerformanceDetailResponse response = performanceService.getPerformanceDetail(performanceId);
        return ResponseEntity.ok(BaseResponse.success("공연 조회에 성공했습니다.", response));
    }
}
