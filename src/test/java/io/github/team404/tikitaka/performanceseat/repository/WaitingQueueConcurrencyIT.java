package io.github.team404.tikitaka.performanceseat.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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

// 다중 인스턴스에서 QueueAdmissionScheduler가 같은 회차를 동시에 처리하는 상황을 실제 Redis(Testcontainers)로
// 재현한다(#117). admitUpToTarget이 원자적(Lua)이지 않았다면, 여러 스레드가 거의 동시에 "목표 - 이미 통과한
// 수"를 계산해 똑같은 slots를 얻고, 그만큼씩 각자 통과시켜 누적 admitted가 admit-count 목표를 넘을 수 있다.
@Testcontainers
@SpringBootTest(classes = WaitingQueueConcurrencyIT.TestConfig.class)
class WaitingQueueConcurrencyIT {

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
    void 여러_스레드가_동시에_호출해도_누적_통과_인원이_입장_허용_목표를_넘지_않는다() throws InterruptedException {
        // given — 목표 100명, 대기 500명
        long scheduleId = System.nanoTime();
        long target = 100;
        int waitingUsers = 500;
        waitingQueueRepository.initAdmitCount(scheduleId, target);
        for (long userId = 1; userId <= waitingUsers; userId++) {
            waitingQueueRepository.addIfAbsent(scheduleId, userId);
        }

        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger totalAdmitted = new AtomicInteger();

        // when — 다중 인스턴스의 동시 스케줄러 틱을 흉내: 같은 회차에 동시에 admitUpToTarget을 호출
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    totalAdmitted.addAndGet(waitingQueueRepository.admitUpToTarget(scheduleId));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // then — 모든 스레드의 반환값 합이 정확히 목표와 같아야 하고(초과 없음), admitted 집합 크기도 일치해야 한다
        assertThat(totalAdmitted.get()).isEqualTo((int) target);
        assertThat(waitingQueueRepository.admittedSize(scheduleId)).isEqualTo(target);
        assertThat(waitingQueueRepository.size(scheduleId)).isEqualTo(waitingUsers - target);
    }

    @Import({RedisAutoConfiguration.class, WaitingQueueRepository.class})
    static class TestConfig {
    }
}
