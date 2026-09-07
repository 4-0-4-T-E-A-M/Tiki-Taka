package io.github.team404.tikitaka.performanceseat.ranking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

// #94: 실제 Redis에서 일 버킷 누적·시간 감쇠 합산·상위 N·동점·유실 대응을 검증한다.
@Testcontainers
@SpringBootTest(classes = PopularPerformanceRankingRepositoryIT.TestConfig.class)
class PopularPerformanceRankingRepositoryIT {

    private static final DateTimeFormatter BUCKET_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private PopularPerformanceRankingRepository rankingRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void flush() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    private String bucketKey(LocalDate date) {
        return "ranking:performance:score:" + date.format(BUCKET_DATE);
    }

    @Test
    void 같은_공연에_점수를_여러_번_더하면_누적된다() {
        rankingRepository.addScore(1L, 1.0);
        rankingRepository.addScore(1L, 1.0);
        rankingRepository.addScore(1L, 10.0);

        Double score = redisTemplate.opsForZSet().score(bucketKey(LocalDate.now()), "1");
        assertThat(score).isEqualTo(12.0);
    }

    @Test
    void 오늘_버킷에_TTL이_설정된다() {
        rankingRepository.addScore(1L, 1.0);

        Long ttl = redisTemplate.getExpire(bucketKey(LocalDate.now()));
        // retention-days 기본 7 + 1일 = 8일 이하, 그리고 설정되어 있어야(>0) 한다
        assertThat(ttl).isPositive().isLessThanOrEqualTo(Duration.ofDays(8).toSeconds());
    }

    @Test
    void 상위_N은_점수_내림차순으로_반환한다() {
        rankingRepository.addScore(1L, 5.0);
        rankingRepository.addScore(2L, 30.0);
        rankingRepository.addScore(3L, 15.0);

        assertThat(rankingRepository.topPerformanceIds(10)).containsExactly(2L, 3L, 1L);
    }

    @Test
    void 지난_날_버킷은_시간_감쇠_가중치로_합산된다() {
        // 오늘: 공연1 = 10점
        rankingRepository.addScore(1L, 10.0);
        // 어제 버킷에 공연2 = 12점을 직접 심는다. 감쇠계수 0.85 → 유효 10.2점 (> 공연1의 10점)
        redisTemplate.opsForZSet().add(bucketKey(LocalDate.now().minusDays(1)), "2", 12.0);

        assertThat(rankingRepository.topPerformanceIds(10)).containsExactly(2L, 1L);
    }

    @Test
    void 동점이면_performanceId_사전순으로_정렬된다() {
        rankingRepository.addScore(10L, 5.0);
        rankingRepository.addScore(2L, 5.0);

        // 문자열 사전순: "10" < "2"
        assertThat(rankingRepository.topPerformanceIds(10)).containsExactly(10L, 2L);
    }

    @Test
    void 데이터가_없으면_빈_목록을_반환한다() {
        assertThat(rankingRepository.topPerformanceIds(10)).isEmpty();
    }

    @Test
    void limit이_0이하면_빈_목록을_반환한다() {
        rankingRepository.addScore(1L, 5.0);

        assertThat(rankingRepository.topPerformanceIds(0)).isEmpty();
    }

    @Test
    void retention_윈도우_밖의_버킷은_합산되지_않는다() {
        rankingRepository.addScore(1L, 5.0);
        // 8일 전(윈도우 7일 밖) 버킷에 큰 점수 — topPerformanceIds가 참조하지 않아야 한다
        redisTemplate.opsForZSet().add(bucketKey(LocalDate.now().minusDays(8)), "2", 999.0);

        List<Long> top = rankingRepository.topPerformanceIds(10);
        assertThat(top).containsExactly(1L);
    }

    @Import({RedisAutoConfiguration.class, PopularPerformanceRankingRepository.class})
    static class TestConfig {
    }
}
