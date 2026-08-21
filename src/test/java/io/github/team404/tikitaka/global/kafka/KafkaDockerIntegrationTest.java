package io.github.team404.tikitaka.global.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import io.github.team404.tikitaka.booking.entity.ReservationStatus;
import io.github.team404.tikitaka.global.kafka.consumer.KafkaEventConsumer;
import io.github.team404.tikitaka.global.kafka.event.ReservationEvent;
import io.github.team404.tikitaka.global.kafka.producer.KafkaEventProducer;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@Tag("docker-kafka")
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=localhost:9092",

        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer",

        "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.JsonDeserializer",

        "spring.kafka.consumer.properties.spring.json.trusted.packages="
                + "io.github.team404.tikitaka.global.kafka.event",

        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.consumer.group-id=docker-kafka-integration-test"
})
@DirtiesContext
class KafkaDockerIntegrationTest {

    @Autowired
    private KafkaEventProducer kafkaEventProducer;

    @MockitoSpyBean
    private KafkaEventConsumer kafkaEventConsumer;

    @Test
    void docker_kafka를_통해_예약_이벤트를_전송하고_수신한다() {

        UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        ReservationEvent event = new ReservationEvent(
                eventId,
                999L,
                100L,
                200L,
                ReservationStatus.CONFIRMED,
                LocalDateTime.of(2026, 8, 18, 17, 0)
        );

        ArgumentCaptor<ReservationEvent> eventCaptor =
                ArgumentCaptor.forClass(ReservationEvent.class);

        kafkaEventProducer.send(event);

        verify(kafkaEventConsumer, timeout(10_000))
                .consume(eventCaptor.capture());

        assertThat(eventCaptor.getValue().eventId()).isEqualTo(eventId);
        assertThat(eventCaptor.getValue()).isEqualTo(event);
    }
}
