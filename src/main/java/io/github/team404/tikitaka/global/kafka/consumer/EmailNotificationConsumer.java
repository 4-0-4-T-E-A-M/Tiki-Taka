package io.github.team404.tikitaka.global.kafka.consumer;

import io.github.team404.tikitaka.global.kafka.event.ReservationEvent;
import io.github.team404.tikitaka.global.kafka.topic.KafkaTopics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class EmailNotificationConsumer {

    @KafkaListener(
            topics = KafkaTopics.RESERVATION_EVENTS,
            groupId = "email-notification-consumer"
    )
    public void consume(ReservationEvent event) {
        log.info("Kafka reservation event received for email notification. reservationId={}",
                event.reservationId());
    }
}
