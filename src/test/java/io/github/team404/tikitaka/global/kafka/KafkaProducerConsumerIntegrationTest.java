package io.github.team404.tikitaka.global.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import io.github.team404.tikitaka.booking.entity.ReservationStatus;
import io.github.team404.tikitaka.global.kafka.consumer.KafkaEventConsumer;
import io.github.team404.tikitaka.global.kafka.repository.ProcessedEventRepository;
import io.github.team404.tikitaka.global.kafka.repository.ReservationStatisticsRepository;
import io.github.team404.tikitaka.global.kafka.event.ReservationEvent;
import io.github.team404.tikitaka.global.kafka.producer.KafkaEventProducer;
import io.github.team404.tikitaka.global.kafka.topic.KafkaTopics;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest(properties = {
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
@DirtiesContext
class KafkaProducerConsumerIntegrationTest {

    @Autowired
    private KafkaEventProducer kafkaEventProducer;

    @MockitoSpyBean
    private KafkaEventConsumer kafkaEventConsumer;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private ReservationStatisticsRepository reservationStatisticsRepository;

    @BeforeEach
    void clearConsumerInvocations() {
        clearInvocations(kafkaEventConsumer);
    }

    @Test
    void producer가_전송한_예약_이벤트를_consumer가_수신한다() {
        UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        ReservationEvent event = new ReservationEvent(
                eventId,
                1L,
                10L,
                100L,
                ReservationStatus.CONFIRMED,
                LocalDateTime.of(2026, 8, 18, 12, 30)
        );
        ArgumentCaptor<ReservationEvent> eventCaptor = ArgumentCaptor.forClass(ReservationEvent.class);

        kafkaEventProducer.send(event);

        verify(kafkaEventConsumer, timeout(10_000)).consume(eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventId()).isEqualTo(eventId);
        assertThat(eventCaptor.getValue()).isEqualTo(event);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(processedEventRepository.existsById(eventId)).isTrue();
            assertThat(reservationStatisticsRepository.findById(100L).orElseThrow()
                    .getConfirmedCount()).isEqualTo(1L);
        });
    }

    @Test
    void 동일한_eventId를_두번_전송해도_통계는_한번만_증가한다() {
        UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        ReservationEvent event = confirmedEvent(eventId, 101L);

        kafkaEventProducer.send(event);
        kafkaEventProducer.send(event);

        verify(kafkaEventConsumer, timeout(10_000).times(2)).consume(event);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(processedEventRepository.existsById(eventId)).isTrue();
            assertThat(reservationStatisticsRepository.findById(101L).orElseThrow()
                    .getConfirmedCount()).isEqualTo(1L);
        });
    }

    @Test
    void 서로_다른_eventId를_같은_scheduleId로_전송하면_통계가_두번_증가한다() {
        ReservationEvent firstEvent = confirmedEvent(
                UUID.fromString("00000000-0000-0000-0000-000000000003"),
                102L
        );
        ReservationEvent secondEvent = confirmedEvent(
                UUID.fromString("00000000-0000-0000-0000-000000000004"),
                102L
        );

        kafkaEventProducer.send(firstEvent);
        kafkaEventProducer.send(secondEvent);

        verify(kafkaEventConsumer, timeout(10_000)).consume(firstEvent);
        verify(kafkaEventConsumer, timeout(10_000)).consume(secondEvent);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(processedEventRepository.existsById(firstEvent.eventId())).isTrue();
            assertThat(processedEventRepository.existsById(secondEvent.eventId())).isTrue();
            assertThat(reservationStatisticsRepository.findById(102L).orElseThrow()
                    .getConfirmedCount()).isEqualTo(2L);
        });
    }

    @Test
    void CONFIRMED가_아닌_이벤트는_통계에_반영하지_않는다() {
        UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000000005");
        ReservationEvent event = new ReservationEvent(
                eventId,
                1L,
                10L,
                103L,
                ReservationStatus.CANCELED,
                LocalDateTime.of(2026, 8, 18, 12, 30)
        );

        kafkaEventProducer.send(event);

        verify(kafkaEventConsumer, timeout(10_000)).consume(event);
        assertThat(processedEventRepository.existsById(eventId)).isFalse();
        assertThat(reservationStatisticsRepository.findById(103L)).isEmpty();
    }

    private ReservationEvent confirmedEvent(UUID eventId, Long scheduleId) {
        return new ReservationEvent(
                eventId,
                1L,
                10L,
                scheduleId,
                ReservationStatus.CONFIRMED,
                LocalDateTime.of(2026, 8, 18, 12, 30)
        );
    }
}
