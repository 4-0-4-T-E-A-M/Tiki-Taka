package io.github.team404.tikitaka.booking.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.team404.tikitaka.booking.dto.ReservationCreateRequest;
import io.github.team404.tikitaka.booking.dto.ReservationResponse;
import io.github.team404.tikitaka.booking.repository.ReservationRepository;
import io.github.team404.tikitaka.booking.repository.ReservationSeatRepository;
import io.github.team404.tikitaka.global.security.jwt.JwtTokenProvider;
import io.github.team404.tikitaka.performanceseat.entity.Seat;
import io.github.team404.tikitaka.performanceseat.entity.SeatGrade;
import io.github.team404.tikitaka.performanceseat.entity.SeatStatus;
import io.github.team404.tikitaka.performanceseat.repository.SeatRepository;
import io.github.team404.tikitaka.user.domain.UserRole;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ReservationApiIntegrationTest {

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ReservationSeatRepository reservationSeatRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        reservationSeatRepository.deleteAll();
        reservationRepository.deleteAll();
        seatRepository.deleteAll();
    }

    @Test
    void 예약_API를_호출하면_예약과_좌석_매핑이_생성되고_좌석이_HELD_상태가_된다() {
        Seat seat = createAvailableSeat();
        long userId = 1L;
        long simulationId = 1L;
        long scheduleId = 1L;
        ReservationCreateRequest request = createRequest(userId, simulationId, scheduleId, seat.getId());

        ResponseEntity<ReservationResponse> response = restTemplate.exchange(
                "/api/reservations",
                HttpMethod.POST,
                new HttpEntity<>(request, createAuthorizationHeaders(createAccessToken(userId))),
                ReservationResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ReservationResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.id()).isNotNull();
        assertThat(body.userId()).isEqualTo(userId);
        assertThat(body.simulationId()).isEqualTo(simulationId);
        assertThat(body.scheduleId()).isEqualTo(scheduleId);
        assertThat(body.selectedQuantity()).isEqualTo(1);
        assertThat(body.status().name()).isEqualTo("PENDING_PAYMENT");
        assertThat(body.createdAt()).isNotNull();

        assertThat(reservationRepository.count()).isEqualTo(1);
        var reservationSeats = reservationSeatRepository.findAllByReservationId(body.id());
        assertThat(reservationSeats).hasSize(1);
        assertThat(reservationSeats.get(0).getSeatId()).isEqualTo(seat.getId());
        assertThat(seatRepository.findById(seat.getId()).orElseThrow().getStatus())
                .isEqualTo(SeatStatus.HELD);
    }

    @Test
    void 동일_좌석에_100명의_사용자가_동시에_API를_호출하면_한_건만_성공한다()
            throws Exception {

        // given
        Seat seat = createAvailableSeat();
        long seatId = seat.getId();

        int userCount = 100;

        ExecutorService executor =
                Executors.newFixedThreadPool(userCount);

        CountDownLatch readyLatch =
                new CountDownLatch(userCount);

        CountDownLatch startLatch =
                new CountDownLatch(1);

        List<Future<ResponseEntity<String>>> futures =
                new ArrayList<>();

        try {
            // 100명의 사용자 요청 준비
            for (int i = 0; i < userCount; i++) {
                long userId = i + 1L;

                ReservationCreateRequest request =
                        createRequest(
                                userId,
                                1L,
                                1L,
                                seatId
                        );

                String accessToken =
                        createAccessToken(userId);

                futures.add(
                        submitReservationRequest(
                                executor,
                                readyLatch,
                                startLatch,
                                request,
                                accessToken
                        )
                );
            }

            // 100개 작업이 모두 준비될 때까지 기다림
            assertThat(
                    readyLatch.await(
                            10,
                            TimeUnit.SECONDS
                    )
            ).isTrue();

            // 100개 요청 동시 시작
            startLatch.countDown();

            // 모든 HTTP 응답 수집
            List<ResponseEntity<String>> responses =
                    new ArrayList<>();

            for (Future<ResponseEntity<String>> future : futures) {
                responses.add(
                        future.get(
                                30,
                                TimeUnit.SECONDS
                        )
                );
            }

            // then: 응답 상태 집계
            long successCount = responses.stream()
                    .filter(response ->
                            response.getStatusCode()
                                    == HttpStatus.CREATED
                    )
                    .count();

            long conflictCount = responses.stream()
                    .filter(response ->
                            response.getStatusCode()
                                    == HttpStatus.CONFLICT
                    )
                    .count();

            long unexpectedCount = responses.stream()
                    .filter(response ->
                            response.getStatusCode()
                                    != HttpStatus.CREATED
                                    && response.getStatusCode()
                                    != HttpStatus.CONFLICT
                    )
                    .count();

            assertThat(successCount).isEqualTo(1);
            assertThat(conflictCount)
                    .isEqualTo(userCount - 1);
            assertThat(unexpectedCount).isZero();

            // 99개의 충돌 응답 형식 확인
            List<ResponseEntity<String>> conflictResponses =
                    responses.stream()
                            .filter(response ->
                                    response.getStatusCode()
                                            == HttpStatus.CONFLICT
                            )
                            .toList();

            assertThat(conflictResponses)
                    .hasSize(userCount - 1);

            for (ResponseEntity<String> conflictResponse
                    : conflictResponses) {

                JsonNode error =
                        objectMapper.readTree(
                                conflictResponse.getBody()
                        );

                assertThat(error.path("code").asText())
                        .isEqualTo("CONFLICT");

                assertThat(error.path("message").asText())
                        .contains(
                                "이미 다른 사용자가 선점 중인 좌석입니다"
                        )
                        .contains(String.valueOf(seatId));

                assertThat(error.hasNonNull("timestamp"))
                        .isTrue();

                assertThat(error.path("timestamp").asText())
                        .isNotBlank();
            }

            // DB에는 예약이 정확히 1건만 존재
            assertThat(reservationRepository.count())
                    .isEqualTo(1);

            Seat persistedSeat =
                    seatRepository.findById(seatId)
                            .orElseThrow();

            assertThat(persistedSeat.getStatus())
                    .isEqualTo(SeatStatus.HELD);

            // 성공 응답에서 예약 ID 추출
            ResponseEntity<String> successResponse =
                    responses.stream()
                            .filter(response ->
                                    response.getStatusCode()
                                            == HttpStatus.CREATED
                            )
                            .findFirst()
                            .orElseThrow();

            ReservationResponse reservation =
                    objectMapper.readValue(
                            successResponse.getBody(),
                            ReservationResponse.class
                    );

            var reservationSeats =
                    reservationSeatRepository
                            .findAllByReservationId(
                                    reservation.id()
                            );

            assertThat(reservationSeats).hasSize(1);

            assertThat(
                    reservationSeats.get(0).getSeatId()
            ).isEqualTo(seatId);

        } finally {
            executor.shutdownNow();

            assertThat(
                    executor.awaitTermination(
                            10,
                            TimeUnit.SECONDS
                    )
            ).isTrue();
        }
    }

    @Test
    void 락_획득_후_좌석_조회에서_예외가_발생해도_락은_해제된다() {
        long nonExistingSeatId = 999_999L;
        ReservationCreateRequest request = createRequest(1L, 1L, 1L, nonExistingSeatId);

        ResponseEntity<String> response = sendReservationRequest(request, createAccessToken(1L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        RLock lock = redissonClient.getLock("seat-lock:" + nonExistingSeatId);
        assertThat(lock.isLocked()).isFalse();
    }

    private Seat createAvailableSeat() {
        Seat seat = seatRepository.save(Seat.builder()
                .sectionId(1L)
                .rowName("A")
                .seatNumber(1)
                .grade(SeatGrade.VIP)
                .price(100_000)
                .build());
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        return seat;
    }

    private String createAccessToken(long userId) {
        return jwtTokenProvider.generateAccessToken(userId, UserRole.USER);
    }

    private HttpHeaders createAuthorizationHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private ReservationCreateRequest createRequest(
            long userId,
            long simulationId,
            long scheduleId,
            long seatId
    ) {
        return new ReservationCreateRequest(userId, simulationId, scheduleId, List.of(seatId));
    }

    private ResponseEntity<String> sendReservationRequest(
            ReservationCreateRequest request,
            String accessToken
    ) {
        return restTemplate.exchange(
                "/api/reservations",
                HttpMethod.POST,
                new HttpEntity<>(request, createAuthorizationHeaders(accessToken)),
                String.class
        );
    }

    private Future<ResponseEntity<String>> submitReservationRequest(
            ExecutorService executor,
            CountDownLatch readyLatch,
            CountDownLatch startLatch,
            ReservationCreateRequest request,
            String accessToken
    ) {
        return executor.submit(() -> {
            readyLatch.countDown();
            assertThat(startLatch.await(5, TimeUnit.SECONDS)).isTrue();
            return sendReservationRequest(request, accessToken);
        });
    }
}
