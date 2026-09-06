package io.github.team404.tikitaka.performanceseat.ranking;

import io.github.team404.tikitaka.global.response.BaseResponse;
import io.github.team404.tikitaka.performanceseat.dto.PerformanceResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 인기 공연 랭킹 조회 (#94). /api/performances/popular 는 /{performanceId} 보다 구체적인 패턴이라
// 라우팅이 충돌하지 않는다.
@RestController
@RequestMapping("/api/performances/popular")
@RequiredArgsConstructor
public class PopularPerformanceController {

    private static final int MAX_LIMIT = 50;

    private final PopularPerformanceRankingService rankingService;

    @GetMapping
    public ResponseEntity<BaseResponse<List<PerformanceResponse>>> popular(
            @RequestParam(defaultValue = "10") int limit) {
        int bounded = Math.max(1, Math.min(limit, MAX_LIMIT));
        return ResponseEntity.ok(BaseResponse.success(
                "인기 공연 조회에 성공했습니다.", rankingService.topPerformances(bounded)));
    }
}
