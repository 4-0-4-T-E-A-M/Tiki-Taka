package io.github.team404.tikitaka.performanceseat.ranking;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.zset.Aggregate;
import org.springframework.data.redis.connection.zset.Weights;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Repository;

// 인기 공연 랭킹 저장소 (#94). Redis Sorted Set에 "일(day) 버킷"으로 점수를 누적한다.
// - member = performanceId(문자열), score = 그날의 가중 점수 합
// - 조회 시 최근 N일 버킷을 시간 감쇠 가중치로 합산(ZUNION)해 상위권을 뽑는다 → 오래된 공연 고착 방지
// - 버킷 키에 TTL을 걸어 자동 정리 → Redis가 비어도(유실) 트래픽으로 자연 재구축되는 근사 랭킹
// 키 공간은 대기열(queue:*)·캐시(performance:detail:*)와 분리한다(ranking:*).
@Repository
@RequiredArgsConstructor
public class PopularPerformanceRankingRepository {

    private static final DateTimeFormatter BUCKET_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final StringRedisTemplate redisTemplate;

    // 조회 시 합산할 일 버킷 수. 버킷 TTL은 이 값 + 1일로 잡아 경계에서 먼저 만료되는 것을 막는다.
    @Value("${performance.ranking.retention-days:7}")
    private int retentionDays;

    // 하루 지날수록 곱해지는 감쇠 계수 (오늘=1, 어제=decay, 그저께=decay^2 ...).
    @Value("${performance.ranking.decay-factor:0.85}")
    private double decayFactor;

    private String bucketKey(LocalDate date) {
        return "ranking:performance:score:" + date.format(BUCKET_DATE);
    }

    // 오늘 버킷에 점수를 더하고 버킷 TTL을 갱신한다.
    public void addScore(Long performanceId, double weight) {
        String key = bucketKey(LocalDate.now());
        redisTemplate.opsForZSet().incrementScore(key, performanceId.toString(), weight);
        redisTemplate.expire(key, Duration.ofDays(retentionDays + 1L));
    }

    // 최근 retentionDays일 버킷을 감쇠 가중치로 합산해 상위 limit개 performanceId를 점수 내림차순으로 반환.
    // 동점이면 member(performanceId 문자열) 사전순 — Redis Sorted Set의 정렬 규칙과 동일하게 맞춘다.
    public List<Long> topPerformanceIds(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        LocalDate today = LocalDate.now();

        List<String> otherKeys = new ArrayList<>();
        List<Double> weightList = new ArrayList<>();
        weightList.add(1.0);
        for (int daysAgo = 1; daysAgo < retentionDays; daysAgo++) {
            otherKeys.add(bucketKey(today.minusDays(daysAgo)));
            weightList.add(Math.pow(decayFactor, daysAgo));
        }

        Set<TypedTuple<String>> union = redisTemplate.opsForZSet().unionWithScores(
                bucketKey(today), otherKeys, Aggregate.SUM, Weights.of(toDoubleArray(weightList)));

        if (union == null || union.isEmpty()) {
            return List.of();
        }
        return union.stream()
                .filter(t -> t.getValue() != null && t.getScore() != null)
                .sorted(Comparator
                        .comparingDouble((TypedTuple<String> t) -> t.getScore()).reversed()
                        .thenComparing(TypedTuple::getValue))
                .limit(limit)
                .map(t -> Long.parseLong(Objects.requireNonNull(t.getValue())))
                .toList();
    }

    private double[] toDoubleArray(List<Double> values) {
        double[] result = new double[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i);
        }
        return result;
    }
}
