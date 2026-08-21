package io.github.team404.tikitaka.global.kafka.producer;

import io.github.team404.tikitaka.global.kafka.event.ReservationEvent;
import io.github.team404.tikitaka.global.kafka.topic.KafkaTopics;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KafkaEventProducer {

    private final KafkaTemplate<String, ReservationEvent> kafkaTemplate;

    public void send(ReservationEvent event) {
        kafkaTemplate.send(KafkaTopics.RESERVATION_EVENTS, event);
    }
}
