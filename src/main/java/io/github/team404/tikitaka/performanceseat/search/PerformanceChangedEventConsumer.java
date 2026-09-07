package io.github.team404.tikitaka.performanceseat.search;

import io.github.team404.tikitaka.global.kafka.event.PerformanceChangedEvent;
import io.github.team404.tikitaka.global.kafka.topic.KafkaTopics;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

// 공연 변경 이벤트 → 검색 인덱스 반영 (#90). UPSERT는 재조회 후 색인, DELETE는 id로 삭제.
@Component
@RequiredArgsConstructor
public class PerformanceChangedEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PerformanceChangedEventConsumer.class);

    private final PerformanceSearchIndexer indexer;

    @KafkaListener(topics = KafkaTopics.PERFORMANCE_EVENTS, groupId = "performance-search-indexer")
    public void consume(PerformanceChangedEvent event) {
        log.info("공연 변경 이벤트 수신: {}", event);
        switch (event.changeType()) {
            case UPSERT -> indexer.indexById(event.performanceId());
            case DELETE -> indexer.deleteById(event.performanceId());
        }
    }
}
