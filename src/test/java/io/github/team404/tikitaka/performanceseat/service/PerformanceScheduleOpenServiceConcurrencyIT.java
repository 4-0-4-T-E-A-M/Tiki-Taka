package io.github.team404.tikitaka.performanceseat.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.team404.tikitaka.performanceseat.entity.PerformanceSchedule;
import io.github.team404.tikitaka.performanceseat.entity.ScheduleStatus;
import io.github.team404.tikitaka.performanceseat.repository.PerformanceScheduleRepository;
import io.github.team404.tikitaka.performanceseat.repository.WaitingQueueRepository;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

// 다중 인스턴스에서 PerformanceOpenScheduler가 같은 회차를 동시에 집는 상황을 재현한다(#117).
// PerformanceScheduleRepository.openIfScheduled의 조건부 UPDATE(WHERE status = SCHEDULED)가 없었다면,
// 여러 스레드가 같은 SCHEDULED 행을 읽고 각자 open()으로 메모리 상태만 바꿔 커밋하므로(최종값은 모두
// OPEN이라 데이터 손상은 없지만) 모두 "전환 성공"으로 오인해 오픈 로그·대기열 초기화 호출이 중복된다.
@SpringBootTest
@Testcontainers
class PerformanceScheduleOpenServiceConcurrencyIT {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private PerformanceScheduleOpenService performanceScheduleOpenService;

    @Autowired
    private PerformanceScheduleRepository scheduleRepository;

    @Autowired
    private WaitingQueueRepository waitingQueueRepository;

    private Long scheduleId;

    @Test
    void 같은_회차를_여러_스레드가_동시에_열어도_오픈_반영은_정확히_한_번만_일어난다() throws InterruptedException {
        // given — 오픈 시각이 지난 SCHEDULED 회차 하나
        PerformanceSchedule schedule = scheduleRepository.save(PerformanceSchedule.builder()
                .performanceId(1L)
                .performanceDatetime(LocalDateTime.now().plusDays(7))
                .openAt(LocalDateTime.now().minusMinutes(1))
                .build());
        scheduleId = schedule.getId();

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger totalOpened = new AtomicInteger();

        // when — 같은 회차를 동시에 여러 스레드(=여러 인스턴스를 흉내)가 오픈 처리
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    totalOpened.addAndGet(performanceScheduleOpenService.openDueSchedules());
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

        // then — "opened" 합은 정확히 1이어야 하고(중복 반영 없음), 회차는 OPEN, 입장 허용 인원도 1번만 초기화됨
        assertThat(totalOpened.get()).isEqualTo(1);
        assertThat(scheduleRepository.findById(scheduleId).orElseThrow().getStatus())
                .isEqualTo(ScheduleStatus.OPEN);
        assertThat(waitingQueueRepository.admitCount(scheduleId)).isEqualTo(100L);
    }

    @AfterEach
    void cleanUp() {
        if (scheduleId != null) {
            scheduleRepository.deleteById(scheduleId);
        }
    }
}
