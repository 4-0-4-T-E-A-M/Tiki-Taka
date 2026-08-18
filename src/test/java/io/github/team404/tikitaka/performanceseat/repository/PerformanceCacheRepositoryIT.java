package io.github.team404.tikitaka.performanceseat.repository;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.team404.tikitaka.performanceseat.dto.PerformanceDetailResponse;
import io.github.team404.tikitaka.performanceseat.dto.PerformanceResponse;
import io.github.team404.tikitaka.performanceseat.entity.PerformanceGenre;
import io.github.team404.tikitaka.performanceseat.entity.PerformanceRegion;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(classes = PerformanceCacheRepositoryIT.TestConfig.class)
class PerformanceCacheRepositoryIT {

    private static final long TEST_TTL_MS = 900_000L;

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("tikitaka.cache.performance-detail-ttl-ms", () -> TEST_TTL_MS);
    }

    @Autowired
    private PerformanceCacheRepository performanceCacheRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void 캐시_미스시_빈값을_반환한다() {
        // when
        Optional<PerformanceDetailResponse> found = performanceCacheRepository.findDetail(System.nanoTime());

        // then
        assertThat(found).isEmpty();
    }

    @Test
    void 저장한_상세를_그대로_조회한다() {
        // given
        long performanceId = System.nanoTime();
        PerformanceDetailResponse detail = new PerformanceDetailResponse(
                new PerformanceResponse(performanceId, "첫 콘서트", "아이유", "체조경기장",
                        PerformanceRegion.SEOUL, PerformanceGenre.CONCERT, "설명", "poster.png", null),
                List.of());

        // when
        performanceCacheRepository.saveDetail(performanceId, detail);
        Optional<PerformanceDetailResponse> found = performanceCacheRepository.findDetail(performanceId);

        // then
        assertThat(found).contains(detail);
    }

    @Test
    void 저장시_설정된_TTL이_적용된다() {
        // given
        long performanceId = System.nanoTime();
        PerformanceDetailResponse detail = new PerformanceDetailResponse(
                new PerformanceResponse(performanceId, "첫 콘서트", "아이유", "체조경기장",
                        PerformanceRegion.SEOUL, PerformanceGenre.CONCERT, "설명", "poster.png", null),
                List.of());

        // when
        performanceCacheRepository.saveDetail(performanceId, detail);
        Long expireSeconds = redisTemplate.getExpire("performance:detail:%d".formatted(performanceId));

        // then
        assertThat(expireSeconds).isNotNull();
        assertThat(expireSeconds).isPositive();
        assertThat(expireSeconds).isLessThanOrEqualTo(Duration.ofMillis(TEST_TTL_MS).toSeconds());
    }

    @Test
    void 무효화하면_이후_조회는_캐시_미스가_된다() {
        // given
        long performanceId = System.nanoTime();
        PerformanceDetailResponse detail = new PerformanceDetailResponse(
                new PerformanceResponse(performanceId, "첫 콘서트", "아이유", "체조경기장",
                        PerformanceRegion.SEOUL, PerformanceGenre.CONCERT, "설명", "poster.png", null),
                List.of());
        performanceCacheRepository.saveDetail(performanceId, detail);

        // when
        performanceCacheRepository.evictDetail(performanceId);

        // then
        assertThat(performanceCacheRepository.findDetail(performanceId)).isEmpty();
    }

    @Import({RedisAutoConfiguration.class, JacksonAutoConfiguration.class, PerformanceCacheRepository.class})
    static class TestConfig {
    }
}
