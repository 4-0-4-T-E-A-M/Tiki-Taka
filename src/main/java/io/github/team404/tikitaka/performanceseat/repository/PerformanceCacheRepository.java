package io.github.team404.tikitaka.performanceseat.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.team404.tikitaka.performanceseat.dto.PerformanceDetailResponse;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

// 공연 상세 조회 Cache-Aside 저장소. 공연 오픈 시점처럼 동일 공연 상세를 다수가 동시에 조회하는
// 트래픽(이슈 #56)에 대응하기 위해 공연 ID 단위로 캐싱한다.
//
// TTL·무효화 전략(이슈 #57): 인기 공연 상세는 오픈 전후로 정보가 거의 바뀌지 않는다는 특성을
// 감안해 TTL을 길게 가져가 캐시 히트율을 우선한다(기본값 15분, tikitaka.cache.performance-detail-ttl-ms
// 로 조정). 대신 드물게 발생하는 수정/삭제는 PerformanceService에서 evictDetail로 즉시 반영해
// TTL을 길게 가져가는 데 따른 최신성 문제를 보완한다. 캐시 스탬피드 대응(만료 직전 갱신, 락 등)은
// 이 캐시가 PK 단건 SELECT 미스라 DB 부담이 낮고, 실제 경합 지점(좌석 선점)은 별도 Redis 분산락이
// 담당하므로 현재 트래픽 규모에서는 도입하지 않기로 판단했다.
@Repository
public class PerformanceCacheRepository {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration detailTtl;

    public PerformanceCacheRepository(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${tikitaka.cache.performance-detail-ttl-ms}") long detailTtlMillis) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.detailTtl = Duration.ofMillis(detailTtlMillis);
    }

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
            redisTemplate.opsForValue().set(detailKey(performanceId), json, detailTtl);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void evictDetail(Long performanceId) {
        redisTemplate.delete(detailKey(performanceId));
    }
}
