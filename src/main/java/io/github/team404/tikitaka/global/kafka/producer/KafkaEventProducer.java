package io.github.team404.tikitaka.global.kafka.producer;

import io.github.team404.tikitaka.global.kafka.event.ReservationEvent;
import io.github.team404.tikitaka.global.kafka.topic.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaEventProducer {

    private final KafkaTemplate<String, ReservationEvent> kafkaTemplate;

    // 메시지 키로 reservationId를 써서 같은 예매의 이벤트가 항상 같은 파티션에서 순서대로 처리되게 한다.
    public void send(ReservationEvent event) {
        kafkaTemplate.send(KafkaTopics.RESERVATION_EVENTS, event.reservationId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Kafka 이벤트 발행 실패. topic={}, reservationId={}",
                                KafkaTopics.RESERVATION_EVENTS, event.reservationId(), ex);
                    } else {
                        log.info("Kafka 이벤트 발행 성공. topic={}, reservationId={}, partition={}, offset={}",
                                KafkaTopics.RESERVATION_EVENTS, event.reservationId(),
                                result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
                    }
                });
    }
}
