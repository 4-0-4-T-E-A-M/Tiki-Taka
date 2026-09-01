package io.github.team404.tikitaka.performanceseat.search;

import io.github.team404.tikitaka.performanceseat.repository.PerformanceRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.stereotype.Component;

// PostgreSQL → Elasticsearch 색인 반영. 항상 공연 하나를 통째로 upsert한다(부분 업데이트 없음).
// 이벤트 소비자와 배치 재색인이 공유하는 진입점 (docs/tradeoffs/search/db-es-sync-strategy.md).
@Component
@RequiredArgsConstructor
public class PerformanceSearchIndexer {

    private static final Logger log = LoggerFactory.getLogger(PerformanceSearchIndexer.class);

    private final PerformanceRepository performanceRepository;
    private final PerformanceSearchRepository searchRepository;
    private final ElasticsearchOperations elasticsearchOperations;

    // 인덱스가 없으면 매핑과 함께 생성. 애플리케이션 기동 시 1회 + 재색인 시 사용 — 존재하면 no-op.
    public void ensureIndex() {
        IndexOperations indexOps = elasticsearchOperations.indexOps(PerformanceDocument.class);
        if (!indexOps.exists()) {
            indexOps.createWithMapping();
            log.info("Elasticsearch 인덱스 생성: performances");
        }
    }

    // UPSERT 이벤트 처리: 그 시점의 PostgreSQL 최신값을 다시 읽어 문서를 덮어쓴다(멱등·순서 무관).
    // 이벤트와 재조회 사이에 삭제됐다면 색인에서도 지운다.
    public void indexById(Long performanceId) {
        performanceRepository.findById(performanceId)
                .ifPresentOrElse(
                        performance -> searchRepository.save(PerformanceDocument.from(performance)),
                        () -> deleteById(performanceId));
    }

    public void deleteById(Long performanceId) {
        searchRepository.deleteById(String.valueOf(performanceId));
    }

    // 안전망: PostgreSQL 전체를 색인에 다시 밀어넣어 이벤트 유실로 생긴 드리프트를 원상 복구한다.
    // 운영에서는 새 인덱스에 색인 후 alias를 전환하는 무중단 방식을 쓰지만(문서 참고),
    // 이 프로젝트 규모에서는 재생성 후 bulk 색인으로 충분하다. 반영한 문서 수를 반환.
    public long reindexAll() {
        IndexOperations indexOps = elasticsearchOperations.indexOps(PerformanceDocument.class);
        if (indexOps.exists()) {
            indexOps.delete();
        }
        indexOps.createWithMapping();

        List<PerformanceDocument> documents = performanceRepository.findAll().stream()
                .map(PerformanceDocument::from)
                .toList();
        if (!documents.isEmpty()) {
            searchRepository.saveAll(documents);
        }
        log.info("공연 검색 전체 재색인 완료: {}건", documents.size());
        return documents.size();
    }
}
