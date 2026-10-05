package io.github.team404.tikitaka.performanceseat.search;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.team404.tikitaka.performanceseat.dto.PerformanceSearchResponse;
import io.github.team404.tikitaka.performanceseat.entity.PerformanceGenre;
import io.github.team404.tikitaka.performanceseat.entity.PerformanceRegion;
import io.github.team404.tikitaka.performanceseat.repository.PerformanceRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// 이슈 #116 실측 벤치마크: 공연 검색(GET /api/performances/search)의 ES 경로 응답 시간을
// 대표 쿼리 모양(키워드만 / 키워드+필터 / 필터만 / 무조건 전체조회) 별로 잰다.
//
// src/test/resources/application.yaml 기본값은 ES uri가 없는 placeholder라, 이 테스트만
// @DynamicPropertySource로 실제 로컬 ES(docker compose, nori 설치된 이미지)로 접속을 덮어쓴다.
//
// 로컬 docker compose(elasticsearch, docker/elasticsearch/Dockerfile 기준 nori 포함 이미지)가
// 떠 있을 때만 수동으로 실행한다. 결과는 docs/perf/elasticsearch-search-response-time.md 에 기록한다.
@SpringBootTest
@Disabled("로컬 docker compose(elasticsearch, nori 포함 이미지) 기동 중에만 수동 실행하는 실측 벤치마크. "
        + "재측정 시 이 줄만 지우고 단독 실행할 것: "
        + "./gradlew test --tests \"*.PerformanceSearchResponseTimeBenchmarkIT\"")
class PerformanceSearchResponseTimeBenchmarkIT {

    private static final int ROUNDS = 30;
    private static final int DOC_COUNT = 2_000;

    @DynamicPropertySource
    static void realLocalElasticsearch(DynamicPropertyRegistry registry) {
        registry.add("spring.elasticsearch.uris", () -> "http://localhost:9200");
    }

    @Autowired
    private PerformanceSearchRepository searchRepository;

    @Autowired
    private PerformanceSearchService searchService;

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    // ES 정상 경로만 측정 대상이라 폴백이 끼어들지 않도록 PostgreSQL 쪽은 쓰지 않는다
    // (PerformanceSearchIT와 동일한 이유로 Mock 처리).
    @MockitoBean
    private PerformanceRepository performanceRepository;

    @Test
    void 대표_쿼리_모양별_응답시간을_측정한다() {
        var indexOps = elasticsearchOperations.indexOps(PerformanceDocument.class);
        if (indexOps.exists()) {
            indexOps.delete();
        }
        indexOps.createWithMapping();

        try {
            seedDocuments(DOC_COUNT);
            indexOps.refresh();

            // 워밍업 5회(JIT·커넥션 예열, 측정 제외) — 네 가지 쿼리 모양을 섞어서 돌린다
            for (int i = 0; i < 5; i++) {
                searchService.search("콘서트", null, null, PageRequest.of(0, 20));
                searchService.search(null, PerformanceGenre.CONCERT, PerformanceRegion.SEOUL, PageRequest.of(0, 20));
                searchService.search(null, null, null, PageRequest.of(0, 20));
            }

            List<Long> keywordOnlyNanos = measure(() ->
                    searchService.search("콘서트", null, null, PageRequest.of(0, 20)));
            List<Long> keywordAndFilterNanos = measure(() ->
                    searchService.search("콘서트", PerformanceGenre.CONCERT, PerformanceRegion.SEOUL, PageRequest.of(0, 20)));
            List<Long> filterOnlyNanos = measure(() ->
                    searchService.search(null, PerformanceGenre.CONCERT, PerformanceRegion.SEOUL, PageRequest.of(0, 20)));
            List<Long> browseAllNanos = measure(() ->
                    searchService.search(null, null, null, PageRequest.of(0, 20)));

            printStats("키워드만 (\"콘서트\", 필터 없음)", keywordOnlyNanos);
            printStats("키워드 + 장르·지역 필터", keywordAndFilterNanos);
            printStats("필터만 (키워드 없음, 장르·지역)", filterOnlyNanos);
            printStats("전체 조회 (키워드·필터 없음, 최신순)", browseAllNanos);

            assertThat(keywordOnlyNanos).hasSize(ROUNDS);
        } finally {
            indexOps.delete();
        }
    }

    private List<Long> measure(java.util.function.Supplier<PerformanceSearchResponse> call) {
        List<Long> nanos = new ArrayList<>();
        for (int i = 0; i < ROUNDS; i++) {
            long start = System.nanoTime();
            call.get();
            nanos.add(System.nanoTime() - start);
        }
        return nanos;
    }

    // 장르·지역이 고르게 섞인 공연 DOC_COUNT건을 색인한다. 제목 절반은 "콘서트"를 포함시켜
    // 키워드 검색이 매칭 공연 일부만 상대하도록(전체 스캔이 아니라 실제 필터링이 일어나도록) 한다.
    private void seedDocuments(int count) {
        PerformanceGenre[] genres = PerformanceGenre.values();
        PerformanceRegion[] regions = PerformanceRegion.values();
        List<PerformanceDocument> documents = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            boolean isConcertTitle = i % 2 == 0;
            documents.add(PerformanceDocument.builder()
                    .id(String.valueOf(i))
                    .title((isConcertTitle ? "콘서트 " : "페스티벌 ") + i)
                    .artist("아티스트 " + i)
                    .venueName("공연장 " + (i % 50))
                    .genre(genres[i % genres.length].name())
                    .region(regions[i % regions.length].name())
                    .posterUrl("https://example.com/" + i + ".jpg")
                    .createdAt(LocalDateTime.now().minusMinutes(i))
                    .build());
        }
        searchRepository.saveAll(documents);
    }

    private void printStats(String label, List<Long> nanos) {
        List<Long> sorted = new ArrayList<>(nanos);
        Collections.sort(sorted);
        double avgMs = nanos.stream().mapToLong(Long::longValue).average().orElse(0) / 1_000_000.0;
        long minMs = sorted.get(0) / 1_000_000;
        long maxMs = sorted.get(sorted.size() - 1) / 1_000_000;
        long p95Ms = sorted.get((int) (sorted.size() * 0.95)) / 1_000_000;
        System.out.printf("[%s] avg=%.3fms min=%dms max=%dms p95=%dms n=%d%n",
                label, avgMs, minMs, maxMs, p95Ms, nanos.size());
    }
}
