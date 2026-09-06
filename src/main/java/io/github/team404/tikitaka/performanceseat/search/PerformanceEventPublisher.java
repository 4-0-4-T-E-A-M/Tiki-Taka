package io.github.team404.tikitaka.performanceseat.search;

import io.github.team404.tikitaka.global.kafka.event.PerformanceChangedEvent;
import io.github.team404.tikitaka.global.kafka.topic.KafkaTopics;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

// 공연 변경 이벤트 발행. 파티션 키를 performanceId로 두어 같은 공연 이벤트는 순서대로 처리된다.
// 발행 실패가 공연 CRUD 트랜잭션을 깨면 안 된다 — 실패는 로그만 남기고, 배치 재색인이 드리프트를
// 원상 복구한다 (db-es-sync-strategy.md).
@Component
@RequiredArgsConstructor
public class PerformanceEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PerformanceEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(PerformanceChangedEvent event) {
        try {
            kafkaTemplate.send(
                    KafkaTopics.PERFORMANCE_EVENTS,
                    String.valueOf(event.performanceId()),
                    event);
        } catch (RuntimeException e) {
            log.warn("공연 변경 이벤트 발행 실패 — 배치 재색인으로 복구됨. event={}", event, e);
        }
    }
}
