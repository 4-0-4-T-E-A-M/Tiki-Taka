package io.github.team404.tikitaka.performanceseat.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.team404.tikitaka.performanceseat.dto.PerformanceCreateRequest;
import io.github.team404.tikitaka.performanceseat.dto.ScheduleCreateRequest;
import io.github.team404.tikitaka.performanceseat.entity.Performance;
import io.github.team404.tikitaka.performanceseat.entity.PerformanceGenre;
import io.github.team404.tikitaka.performanceseat.entity.PerformanceRegion;
import io.github.team404.tikitaka.performanceseat.repository.PerformanceCacheRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

// 이슈 #53 실측 벤치마크: 공연 상세 조회(GET /api/performances/{id})의 캐시 히트 vs
// 캐시 미스(DB 직접 조회) 응답 시간을 실제 로컬 postgres/redis(docker compose)를 대상으로 잰다.
//
// src/test/resources/application.yaml 기본값은 H2(in-memory)라 Postgres 왕복 비용을
// 반영하지 못하므로, 이 테스트만 @DynamicPropertySource로 실제 로컬 DB로 접속을 덮어쓴다.
// ddl-auto를 반드시 create-drop이 아닌 update로 다시 덮어써야 한다 — 그러지 않으면 테스트
// 컨텍스트 종료 시 로컬 개발 DB 테이블이 전부 DROP된다.
//
// 로컬 docker compose(postgres/redis)가 떠 있을 때만 수동으로 실행한다. 결과는
// docs/perf/performance-detail-cache-vs-db-response-time.md 에 기록했다.
@SpringBootTest
@Disabled("로컬 docker compose(postgres/redis) 기동 중에만 수동 실행하는 실측 벤치마크. "
        + "재측정 시 이 줄만 지우고 단독 실행할 것: "
        + "./gradlew test --tests \"*.PerformanceDetailCacheVsDbBenchmarkIT\"")
class PerformanceDetailCacheVsDbBenchmarkIT {

    private static final int ROUNDS = 30;
    private static final int CONCURRENCY = 20;

    @DynamicPropertySource
    static void realLocalInfra(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:5432/tikitaka");
        registry.add("spring.datasource.username", () -> "tikitaka");
        registry.add("spring.datasource.password", () -> "tikitaka1234");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired
    private PerformanceService performanceService;

    @Autowired
    private PerformanceCacheRepository performanceCacheRepository;

    @Test
    void 캐시_히트와_DB_직접조회_응답시간을_비교한다() throws InterruptedException {
        // given: 실제 상세 응답 크기와 비슷하게 회차 1개를 가진 공연을 하나 만든다
        Performance performance = performanceService.createPerformance(new PerformanceCreateRequest(
                "벤치마크용 콘서트", "벤치마크 아티스트", "체조경기장",
                PerformanceRegion.SEOUL, PerformanceGenre.CONCERT,
                "이슈 #53 캐시 vs DB 응답속도 벤치마크용 데이터",
                "https://example.com/poster.png",
                List.of(new ScheduleCreateRequest(LocalDateTime.now().plusDays(30), List.of()))));
        Long id = performance.getId();

        try {
            // 워밍업: JIT/커넥션 풀을 데운다 (측정에는 포함하지 않음)
            for (int i = 0; i < 5; i++) {
                performanceCacheRepository.evictDetail(id);
                performanceService.getPerformanceDetail(id);
            }

            // 1) 순차 측정 — 캐시 미스(DB 직접 조회)
            List<Long> missNanos = new ArrayList<>();
            for (int i = 0; i < ROUNDS; i++) {
                performanceCacheRepository.evictDetail(id);
                long start = System.nanoTime();
                performanceService.getPerformanceDetail(id);
                missNanos.add(System.nanoTime() - start);
            }

            // 2) 순차 측정 — 캐시 히트
            performanceService.getPerformanceDetail(id); // 캐시 재적재
            List<Long> hitNanos = new ArrayList<>();
            for (int i = 0; i < ROUNDS; i++) {
                long start = System.nanoTime();
                performanceService.getPerformanceDetail(id);
                hitNanos.add(System.nanoTime() - start);
            }

            printStats("CACHE MISS (DB 직접, 순차 " + ROUNDS + "회)", missNanos);
            printStats("CACHE HIT  (Redis, 순차 " + ROUNDS + "회)", hitNanos);

            // 3) 동시 요청 — 콘서트 오픈 순간 다수가 같은 공연 상세를 동시에 조회하는 상황 재현
            //    (PerformanceCacheRepository는 스탬피드 대응을 도입하지 않기로 한 결정 — 이슈 #57)
            performanceCacheRepository.evictDetail(id);
            long coldConcurrentMs = runConcurrent(CONCURRENCY, id);
            long warmConcurrentMs = runConcurrent(CONCURRENCY, id);

            System.out.printf(
                    "[CONCURRENT %d명 동시] cache-cold(evict 직후, 스탬피드 상황)=%dms  cache-warm=%dms%n",
                    CONCURRENCY, coldConcurrentMs, warmConcurrentMs);

            assertThat(missNanos).hasSize(ROUNDS);
            assertThat(hitNanos).hasSize(ROUNDS);
        } finally {
            // cleanup: 벤치마크로 만든 데이터/캐시를 정리해 로컬 DB를 원상복구한다
            performanceService.deletePerformance(id);
        }
    }

    private long runConcurrent(int n, Long performanceId) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(n);

        for (int i = 0; i < n; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    startGate.await();
                    performanceService.getPerformanceDetail(performanceId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        long start = System.nanoTime();
        startGate.countDown();
        done.await(10, TimeUnit.SECONDS);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        pool.shutdown();
        return elapsedMs;
    }

    private void printStats(String label, List<Long> nanos) {
        List<Long> sorted = new ArrayList<>(nanos);
        Collections.sort(sorted);
        double avgMs = nanos.stream().mapToLong(Long::longValue).average().orElse(0) / 1_000_000.0;
        long minMs = sorted.get(0) / 1_000_000;
        long maxMs = sorted.get(sorted.size() - 1) / 1_000_000;
        long p95Ms = sorted.get((int) (sorted.size() * 0.95)) / 1_000_000;
        System.out.printf("[%s] avg=%.3fms min=%dms max=%dms p95=%dms n=%d%n",
                label, avgMs, minMs, maxMs, p95Ms, nanos.size());
    }
}
