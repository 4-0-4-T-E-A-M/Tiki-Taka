package io.github.team404.tikitaka.performanceseat.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.team404.tikitaka.performanceseat.dto.PerformanceDetailResponse;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

// 공연 상세 조회 Cache-Aside 저장소. 공연 오픈 시점처럼 동일 공연 상세를 다수가 동시에 조회하는
// 트래픽(이슈 #56)에 대응하기 위해 공연 ID 단위로 캐싱한다.
// TTL·무효화(수정 시 캐시 갱신 등) 정책은 별도 이슈("캐시 TTL·무효화 전략 결정")에서 확정 예정이라,
// 아래 TTL은 캐시가 무한정 쌓이는 것만 막는 플레이스홀더 값이며 임의로 바꿔도 다음 이슈에서 재조정된다.
@Repository
@RequiredArgsConstructor
public class PerformanceCacheRepository {

    private static final Duration PLACEHOLDER_TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private String detailKey(Long performanceId) {
        return "performance:detail:%d".formatted(performanceId);
    }

    public Optional<PerformanceDetailResponse> findDetail(Long performanceId) {
        String cached = redisTemplate.opsForValue().get(detailKey(performanceId));
        if (cached == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(cached, PerformanceDetailResponse.class));
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void saveDetail(Long performanceId, PerformanceDetailResponse detail) {
        try {
            String json = objectMapper.writeValueAsString(detail);
            redisTemplate.opsForValue().set(detailKey(performanceId), json, PLACEHOLDER_TTL);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
