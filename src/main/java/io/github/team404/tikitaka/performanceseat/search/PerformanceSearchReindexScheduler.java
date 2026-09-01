package io.github.team404.tikitaka.performanceseat.search;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 안전망(B): 이벤트 유실로 생긴 PostgreSQL↔ES 드리프트를 주기적 전체 재색인으로 원상 복구한다.
// 주기는 데이터 규모가 작아 1일 1회로 시작 (db-es-sync-strategy.md의 TBD — 규모 보고 조정).
@Component
@RequiredArgsConstructor
public class PerformanceSearchReindexScheduler {

    private static final Logger log = LoggerFactory.getLogger(PerformanceSearchReindexScheduler.class);

    private final PerformanceSearchIndexer indexer;

    // 기동 시 인덱스가 없으면 매핑과 함께 만들어 둔다 (첫 색인/검색 요청 전에).
    @EventListener(ApplicationReadyEvent.class)
    public void ensureIndexOnStartup() {
        try {
            indexer.ensureIndex();
        } catch (RuntimeException e) {
            log.warn("기동 시 검색 인덱스 확인 실패 — ES 연결 후 재색인/첫 요청 시 생성된다.", e);
        }
    }

    @Scheduled(cron = "${performance.search.reindex-cron:0 0 4 * * *}")
    public void reindexAll() {
        try {
            indexer.reindexAll();
        } catch (RuntimeException e) {
            log.error("공연 검색 전체 재색인 실패", e);
        }
    }
}
