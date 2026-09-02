package io.github.team404.tikitaka.performanceseat.ranking;

import io.github.team404.tikitaka.performanceseat.dto.PerformanceResponse;
import io.github.team404.tikitaka.performanceseat.entity.Performance;
import io.github.team404.tikitaka.performanceseat.repository.PerformanceRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

// 인기 공연 랭킹 (#94). 점수 정책:
// - 조회(상세 진입)   = VIEW_WEIGHT  (약한 신호, 봇·새로고침에 노출됨)
// - 예매 완료          = RESERVATION_WEIGHT (강한 신호)
// 조회수만 쓰면 봇·반복 요청에 취약하고 오래된 인기작이 고착되므로, 예매에 큰 가중치를 주고
// 저장은 일 버킷 + 시간 감쇠로 최근성을 반영한다(Repository 참고).
//
// 랭킹은 "정확한 집계"가 아니라 "근사한 인기 순위"다. Redis가 유실돼도 재구축하지 않고
// 트래픽으로 다시 채워지게 둔다 — 이 트레이드오프의 근거는
// docs/tradeoffs/redis/popular-ranking-sorted-set.md.
@Service
@RequiredArgsConstructor
public class PopularPerformanceRankingService {

    private static final Logger log = LoggerFactory.getLogger(PopularPerformanceRankingService.class);

    private final PopularPerformanceRankingRepository rankingRepository;
    private final PerformanceRepository performanceRepository;

    @Value("${performance.ranking.view-weight:1.0}")
    private double viewWeight;

    @Value("${performance.ranking.reservation-weight:10.0}")
    private double reservationWeight;

    // 랭킹 반영 실패가 조회·예매 흐름을 깨면 안 된다 — 예외를 삼키고 로그만 남긴다.
    public void recordView(Long performanceId) {
        addScoreQuietly(performanceId, viewWeight, "조회");
    }

    public void recordReservation(Long performanceId) {
        addScoreQuietly(performanceId, reservationWeight, "예매");
    }

    private void addScoreQuietly(Long performanceId, double weight, String reason) {
        try {
            rankingRepository.addScore(performanceId, weight);
        } catch (RuntimeException e) {
            log.warn("인기 랭킹 점수 반영 실패({}) — 랭킹은 근사치라 무시. performanceId={}", reason, performanceId, e);
        }
    }

    // 상위 limit개 인기 공연. 삭제된 공연이 버킷에 남아 있을 수 있으므로 넉넉히 뽑아
    // DB에 존재하는 것만 남기고, Redis 랭킹 순서를 그대로 유지한다.
    public List<PerformanceResponse> topPerformances(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        List<Long> rankedIds;
        try {
            rankedIds = rankingRepository.topPerformanceIds(limit * 3);
        } catch (RuntimeException e) {
            log.warn("인기 랭킹 조회 실패 — 빈 목록 반환", e);
            return List.of();
        }
        if (rankedIds.isEmpty()) {
            return List.of();
        }

        Map<Long, Performance> existing = performanceRepository.findAllById(rankedIds).stream()
                .collect(Collectors.toMap(Performance::getId, Function.identity()));

        return rankedIds.stream()
                .map(existing::get)
                .filter(java.util.Objects::nonNull)
                .limit(limit)
                .map(PerformanceResponse::from)
                .toList();
    }
}
