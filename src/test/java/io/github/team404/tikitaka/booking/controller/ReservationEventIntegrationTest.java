package io.github.team404.tikitaka.booking.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import io.github.team404.tikitaka.booking.dto.ReservationCreateRequest;
import io.github.team404.tikitaka.booking.dto.ReservationResponse;
import io.github.team404.tikitaka.booking.entity.ReservationStatus;
import io.github.team404.tikitaka.booking.repository.ReservationRepository;
import io.github.team404.tikitaka.booking.repository.ReservationSeatRepository;
import io.github.team404.tikitaka.global.kafka.consumer.KafkaEventConsumer;
import io.github.team404.tikitaka.global.kafka.event.ReservationEvent;
import io.github.team404.tikitaka.global.kafka.topic.KafkaTopics;
import io.github.team404.tikitaka.global.security.jwt.JwtTokenProvider;
import io.github.team404.tikitaka.performanceseat.entity.Seat;
import io.github.team404.tikitaka.performanceseat.entity.SeatGrade;
import io.github.team404.tikitaka.performanceseat.entity.SeatStatus;
import io.github.team404.tikitaka.performanceseat.repository.SeatRepository;
import io.github.team404.tikitaka.user.domain.UserRole;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

// #92: 실제 @Transactional 커밋 경로(afterCommit)에서 이벤트가 발행되는지 검증.
// ReservationServiceTest의 Mockito 단위 테스트는 트랜잭션이 관리되지 않는 폴백 경로(즉시 발행)만
// 타므로, afterCommit 등록 자체가 동작하는지는 실제 Spring 트랜잭션이 있는 이 통합 테스트가 아니면
// 검증할 수 없다.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer",
        "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.JsonDeserializer",
        "spring.kafka.consumer.properties.spring.json.trusted.packages="
                + "io.github.team404.tikitaka.global.kafka.event",
        "spring.kafka.consumer.auto-offset-reset=earliest"
})
@EmbeddedKafka(
        partitions = 1,
        topics = KafkaTopics.RESERVATION_EVENTS,
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@Testcontainers
@DirtiesContext
class ReservationEventIntegrationTest {

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

    @MockitoSpyBean
    private KafkaEventConsumer kafkaEventConsumer;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        reservationSeatRepository.deleteAll();
        reservationRepository.deleteAll();
        seatRepository.deleteAll();
    }

    @Test
    void 예매에_성공하면_예매_완료_이벤트가_발행되고_컨슈머가_수신한다() {
        Seat seat = createAvailableSeat();
        long userId = 1L;
        long scheduleId = 1L;

        ResponseEntity<ReservationResponse> response = restTemplate.exchange(
                "/api/reservations",
                HttpMethod.POST,
                new HttpEntity<>(createRequest(userId, scheduleId, seat.getId()), authHeaders(userId)),
                ReservationResponse.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long reservationId = response.getBody().id();

        ArgumentCaptor<ReservationEvent> eventCaptor = ArgumentCaptor.forClass(ReservationEvent.class);
        verify(kafkaEventConsumer, timeout(10_000)).consume(eventCaptor.capture());
        ReservationEvent event = eventCaptor.getValue();
        assertThat(event.reservationId()).isEqualTo(reservationId);
        assertThat(event.userId()).isEqualTo(userId);
        assertThat(event.scheduleId()).isEqualTo(scheduleId);
        assertThat(event.status()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
    }

    @Test
    void 예매가_실패하면_이벤트가_발행되지_않는다() {
        long nonExistingSeatId = 999_999L;

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/reservations",
                HttpMethod.POST,
                new HttpEntity<>(createRequest(1L, 1L, nonExistingSeatId), authHeaders(1L)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(kafkaEventConsumer, after(2_000).never()).consume(any());
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

    private ReservationCreateRequest createRequest(long userId, long scheduleId, long seatId) {
        return new ReservationCreateRequest(userId, 1L, scheduleId, List.of(seatId));
    }

    private HttpHeaders authHeaders(long userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtTokenProvider.generateAccessToken(userId, UserRole.USER));
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
