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
    // 재시도·타임아웃은 KafkaProducerReliabilityConfig 기준으로 Producer가 처리하고, 여기서는 최종 실패만 다룬다(#115).
    public void send(ReservationEvent event) {
        try {
            kafkaTemplate.send(KafkaTopics.RESERVATION_EVENTS, event.reservationId().toString(), event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            logFinalFailure(event, ex);
                        } else {
                            log.info("Kafka 이벤트 발행 성공. topic={}, reservationId={}, partition={}, offset={}",
                                    KafkaTopics.RESERVATION_EVENTS, event.reservationId(),
                                    result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
                        }
                    });
        } catch (RuntimeException e) {
            // 메타데이터 조회가 max.block.ms를 넘는 등 send() 호출 자체가 실패한 경우
            logFinalFailure(event, e);
        }
    }

    // 최종 실패 처리 방침: 예매는 이미 커밋됐으므로 되돌리지 않고, 이벤트 전체를 로그로 남겨 수동 재발행이 가능하게 한다.
    // 같은 eventId로 재발행하면 Consumer의 eventId 멱등 처리로 중복 반영이 막힌다.
    // 유실 자체를 막는 Transactional Outbox는 현재 규모 대비 비용이 커서 도입하지 않았다(이슈 #115).
    private void logFinalFailure(ReservationEvent event, Throwable cause) {
        log.error("Kafka 이벤트 발행 최종 실패 — 수동 재발행 필요. topic={}, event={}",
                KafkaTopics.RESERVATION_EVENTS, event, cause);
    }
}
