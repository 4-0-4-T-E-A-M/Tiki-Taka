package io.github.team404.tikitaka.global.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import io.github.team404.tikitaka.booking.entity.ReservationStatus;
import io.github.team404.tikitaka.global.kafka.consumer.KafkaEventConsumer;
import io.github.team404.tikitaka.global.kafka.event.ReservationEvent;
import io.github.team404.tikitaka.global.kafka.producer.KafkaEventProducer;
import io.github.team404.tikitaka.global.kafka.topic.KafkaTopics;
import java.time.LocalDateTime;
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

    @Test
    void producer가_전송한_예약_이벤트를_consumer가_수신한다() {
        ReservationEvent event = new ReservationEvent(
                1L,
                10L,
                100L,
                ReservationStatus.CONFIRMED,
                LocalDateTime.of(2026, 8, 18, 12, 30)
        );
        ArgumentCaptor<ReservationEvent> eventCaptor = ArgumentCaptor.forClass(ReservationEvent.class);

        kafkaEventProducer.send(event);

        verify(kafkaEventConsumer, timeout(10_000)).consume(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isEqualTo(event);
    }
}
