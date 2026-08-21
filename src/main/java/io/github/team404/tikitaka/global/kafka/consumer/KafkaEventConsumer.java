package io.github.team404.tikitaka.global.kafka.consumer;

import io.github.team404.tikitaka.global.kafka.event.ReservationEvent;
import io.github.team404.tikitaka.global.kafka.service.ReservationStatisticsService;
import io.github.team404.tikitaka.global.kafka.topic.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaEventConsumer {

    private final ReservationStatisticsService reservationStatisticsService;

    @KafkaListener(
            topics = KafkaTopics.RESERVATION_EVENTS,
            groupId = "reservation-event-consumer"
    )
    public void consume(ReservationEvent event) {
        log.info("Kafka reservation event received. reservationId={}", event.reservationId());
        reservationStatisticsService.process(event);
    }
}
