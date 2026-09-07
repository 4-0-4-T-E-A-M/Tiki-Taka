package io.github.team404.tikitaka.performanceseat.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(classes = WaitingQueueRepositoryIT.TestConfig.class)
class WaitingQueueRepositoryIT {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private WaitingQueueRepository waitingQueueRepository;

    @Test
    void 같은_사용자가_두번_진입해도_최초_순번을_유지한다() {
        // given
        long scheduleId = System.nanoTime();

        // when
        boolean firstAdded = waitingQueueRepository.addIfAbsent(scheduleId, 100L);
        Long firstRank = waitingQueueRepository.rank(scheduleId, 100L);
        boolean secondAdded = waitingQueueRepository.addIfAbsent(scheduleId, 100L);
        Long rankAfterRetry = waitingQueueRepository.rank(scheduleId, 100L);

        // then
        assertThat(firstAdded).isTrue();
        assertThat(secondAdded).isFalse();
        assertThat(rankAfterRetry).isEqualTo(firstRank);
    }

    @Test
    void 진입한_순서대로_순번이_매겨진다() {
        // given
        long scheduleId = System.nanoTime();

        // when
        waitingQueueRepository.addIfAbsent(scheduleId, 1L);
        waitingQueueRepository.addIfAbsent(scheduleId, 2L);
        waitingQueueRepository.addIfAbsent(scheduleId, 3L);

        // then
        assertThat(waitingQueueRepository.rank(scheduleId, 1L)).isEqualTo(0L);
        assertThat(waitingQueueRepository.rank(scheduleId, 2L)).isEqualTo(1L);
        assertThat(waitingQueueRepository.rank(scheduleId, 3L)).isEqualTo(2L);
        assertThat(waitingQueueRepository.size(scheduleId)).isEqualTo(3L);
    }

    @Test
    void 앞에서부터_통과시키면_대기열에서_빠지고_뒤_순번이_당겨진다() {
        // given — 5명 대기
        long scheduleId = System.nanoTime();
        for (long userId = 1; userId <= 5; userId++) {
            waitingQueueRepository.addIfAbsent(scheduleId, userId);
        }

        // when — 앞 2명 통과
        int moved = waitingQueueRepository.admitFront(scheduleId, 2);

        // then
        assertThat(moved).isEqualTo(2);
        assertThat(waitingQueueRepository.size(scheduleId)).isEqualTo(3L);
        assertThat(waitingQueueRepository.admittedSize(scheduleId)).isEqualTo(2L);
        assertThat(waitingQueueRepository.isAdmitted(scheduleId, 1L)).isTrue();
        assertThat(waitingQueueRepository.isAdmitted(scheduleId, 2L)).isTrue();
        assertThat(waitingQueueRepository.isAdmitted(scheduleId, 3L)).isFalse();
        // 3번 사용자의 순번이 2 → 0으로 당겨짐
        assertThat(waitingQueueRepository.rank(scheduleId, 3L)).isEqualTo(0L);
    }

    @Test
    void 대기열보다_많은_수를_통과시키면_남은_인원만_옮긴다() {
        // given
        long scheduleId = System.nanoTime();
        waitingQueueRepository.addIfAbsent(scheduleId, 1L);
        waitingQueueRepository.addIfAbsent(scheduleId, 2L);

        // when
        int moved = waitingQueueRepository.admitFront(scheduleId, 10);

        // then
        assertThat(moved).isEqualTo(2);
        assertThat(waitingQueueRepository.size(scheduleId)).isZero();
        assertThat(waitingQueueRepository.admittedSize(scheduleId)).isEqualTo(2L);
    }

    @Test
    void 빈_대기열을_통과시키면_아무것도_옮기지_않는다() {
        long scheduleId = System.nanoTime();

        assertThat(waitingQueueRepository.admitFront(scheduleId, 5)).isZero();
        assertThat(waitingQueueRepository.admittedSize(scheduleId)).isZero();
    }

    @Import({RedisAutoConfiguration.class, WaitingQueueRepository.class})
    static class TestConfig {
    }
}
