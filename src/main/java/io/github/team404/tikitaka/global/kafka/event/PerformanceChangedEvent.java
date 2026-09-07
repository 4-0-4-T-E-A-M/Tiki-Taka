package io.github.team404.tikitaka.global.kafka.event;

// 공연 검색 인덱스 동기화 이벤트 (docs/tradeoffs/search/db-es-sync-strategy.md, #89).
// 필드 값이 아니라 performanceId + 변경 종류만 싣는다 — 소비자가 UPSERT 시 PostgreSQL을 다시
// 조회해 색인하므로 중복 수신·순서 역전에 멱등하다.
public record PerformanceChangedEvent(Long performanceId, ChangeType changeType) {

    public enum ChangeType {
        UPSERT,
        DELETE
    }

    public static PerformanceChangedEvent upsert(Long performanceId) {
        return new PerformanceChangedEvent(performanceId, ChangeType.UPSERT);
    }

    public static PerformanceChangedEvent delete(Long performanceId) {
        return new PerformanceChangedEvent(performanceId, ChangeType.DELETE);
    }
}
