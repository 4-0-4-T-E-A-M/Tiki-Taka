package io.github.team404.tikitaka.global.kafka.topic;

public final class KafkaTopics {

    public static final String RESERVATION_EVENTS = "reservation-events";

    // 공연(Performance) 변경 → 검색 인덱스 동기화 (#89/#90)
    public static final String PERFORMANCE_EVENTS = "performance-events";

    private KafkaTopics() {
    }
}
