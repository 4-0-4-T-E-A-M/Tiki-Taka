package io.github.team404.tikitaka.booking.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.team404.tikitaka.booking.dto.ReservationCreateRequest;
import io.github.team404.tikitaka.booking.entity.ReservationSeat;
import io.github.team404.tikitaka.booking.repository.ReservationRepository;
import io.github.team404.tikitaka.booking.repository.ReservationSeatRepository;
import io.github.team404.tikitaka.performanceseat.entity.Seat;
import io.github.team404.tikitaka.performanceseat.entity.SeatGrade;
import io.github.team404.tikitaka.performanceseat.entity.SeatStatus;
import io.github.team404.tikitaka.performanceseat.repository.SeatRepository;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

// 실제 Redis(Testcontainers) 기반 동시성 검증 — booking/CLAUDE.md의 테스트 기준에 따라 Mock으로 대체하지 않는다.
// 2스레드 테스트(#34)는 락이 실제로 동작하는지에 대한 최소 검증, 100스레드 테스트(#35)는
// 이슈 체크리스트대로 성공/실패 건수와 DB 최종 상태까지 검증한다.
@SpringBootTest
@Testcontainers
class ReservationConcurrencyTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ReservationSeatRepository reservationSeatRepository;

    private Long seatId;
    private final List<Long> createdReservationIds = new CopyOnWriteArrayList<>();

    @Test
    void 동일_좌석에_동시_요청이_오면_한_건만_성공하고_나머지는_충돌_응답을_받는다() throws InterruptedException {
        // given
        Seat seat = seatRepository.save(Seat.builder()
                .sectionId(1L)
                .rowName("A")
                .seatNumber(1)
                .grade(SeatGrade.VIP)
                .price(100_000)
                .build());
        seatId = seat.getId();

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();

        for (int i = 0; i < threadCount; i++) {
            long userId = i + 1L;
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    var reservation = reservationService.createReservation(
                            new ReservationCreateRequest(userId, 1L, 1L, List.of(seatId)));
                    createdReservationIds.add(reservation.getId());
                    successCount.incrementAndGet();
                } catch (ResponseStatusException e) {
                    if (e.getStatusCode() == HttpStatus.CONFLICT) {
                        conflictCount.incrementAndGet();
                    }
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

        // then: 락 경합에서 정확히 1건만 성공하고, 나머지는 즉시 409(CONFLICT)로 실패해야 한다
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(threadCount - 1);
    }

    @Test
    void 동일_좌석에_100명이_동시에_요청하면_한_건만_성공하고_좌석은_HELD_상태로_남는다() throws InterruptedException {
        // given
        Seat seat = seatRepository.save(Seat.builder()
                .sectionId(1L)
                .rowName("A")
                .seatNumber(1)
                .grade(SeatGrade.VIP)
                .price(100_000)
                .build());
        seatId = seat.getId();

        int threadCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();
        AtomicInteger unexpectedFailureCount = new AtomicInteger();

        for (int i = 0; i < threadCount; i++) {
            long userId = i + 1L;
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    var reservation = reservationService.createReservation(
                            new ReservationCreateRequest(userId, 1L, 1L, List.of(seatId)));
                    createdReservationIds.add(reservation.getId());
                    successCount.incrementAndGet();
                } catch (ResponseStatusException e) {
                    if (e.getStatusCode() == HttpStatus.CONFLICT) {
                        conflictCount.incrementAndGet();
                    } else {
                        unexpectedFailureCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        // then: 성공 1건 / 실패(CONFLICT) 99건, 그 외 형태의 실패는 없어야 한다
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(threadCount - 1);
        assertThat(unexpectedFailureCount.get()).isZero();

        // then: 테스트 종료 후 DB 최종 상태 — 좌석은 HELD 1건, 예매-좌석 매핑도 1건이어야 한다
        Seat persistedSeat = seatRepository.findById(seatId).orElseThrow();
        assertThat(persistedSeat.getStatus()).isEqualTo(SeatStatus.HELD);
        assertThat(createdReservationIds).hasSize(1);

        List<ReservationSeat> reservationSeats =
                reservationSeatRepository.findAllByReservationId(createdReservationIds.get(0));
        assertThat(reservationSeats).hasSize(1);
        assertThat(reservationSeats.get(0).getSeatId()).isEqualTo(seatId);
    }

    @AfterEach
    void cleanUp() {
        for (Long reservationId : createdReservationIds) {
            reservationSeatRepository.findAllByReservationId(reservationId)
                    .forEach(reservationSeat -> reservationSeatRepository.deleteById(reservationSeat.getId()));
            reservationRepository.deleteById(reservationId);
        }
        if (seatId != null) {
            seatRepository.deleteById(seatId);
        }
    }
}
