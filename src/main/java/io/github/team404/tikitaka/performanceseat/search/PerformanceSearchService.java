package io.github.team404.tikitaka.performanceseat.search;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import io.github.team404.tikitaka.performanceseat.dto.PerformanceResponse;
import io.github.team404.tikitaka.performanceseat.dto.PerformanceSearchResponse;
import io.github.team404.tikitaka.performanceseat.entity.PerformanceGenre;
import io.github.team404.tikitaka.performanceseat.entity.PerformanceRegion;
import io.github.team404.tikitaka.performanceseat.repository.PerformanceRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

// 공연 키워드 검색. 기본 경로는 Elasticsearch(nori); ES 장애 시 PostgreSQL(QueryDSL)로 폴백하고
// 응답에 degraded=true를 표시한다 (docs/tradeoffs/search/queryDSL-vs-es-role-split.md, #93).
// 구조화 필터만 있는 조회는 QueryDSL 경로가 직접 담당한다.
@Service
@RequiredArgsConstructor
public class PerformanceSearchService {

    private static final Logger log = LoggerFactory.getLogger(PerformanceSearchService.class);

    private final ElasticsearchOperations elasticsearchOperations;
    private final PerformanceRepository performanceRepository;

    public PerformanceSearchResponse search(
            String keyword, PerformanceGenre genre, PerformanceRegion region, Pageable pageable) {
        try {
            return PerformanceSearchResponse.of(searchViaElasticsearch(keyword, genre, region, pageable), false);
        } catch (RuntimeException e) {
            // 운영 단순성 우선: ES 예외는 종류를 가리지 않고 폴백한다. 우리가 만든 쿼리라
            // 400(bad query)은 사실상 나지 않고, 나머지는 모두 "ES 장애"로 취급한다.
            log.warn("Elasticsearch 검색 실패 — PostgreSQL 폴백. keyword={}, genre={}, region={}",
                    keyword, genre, region, e);
            return PerformanceSearchResponse.of(searchViaPostgres(keyword, genre, region, pageable), true);
        }
    }

    private Page<PerformanceResponse> searchViaElasticsearch(
            String keyword, PerformanceGenre genre, PerformanceRegion region, Pageable pageable) {

        Query query = Query.of(q -> q.bool(bool -> {
            if (StringUtils.hasText(keyword)) {
                // 제목·아티스트를 공연장보다 우선. nori 분석 필드라 형태소 단위로 매칭된다.
                bool.must(must -> must.multiMatch(mm -> mm
                        .query(keyword)
                        .fields("title^2", "artist^2", "venueName")));
            } else {
                bool.must(must -> must.matchAll(all -> all));
            }
            if (genre != null) {
                bool.filter(f -> f.term(t -> t.field("genre").value(genre.name())));
            }
            if (region != null) {
                bool.filter(f -> f.term(t -> t.field("region").value(region.name())));
            }
            return bool;
        }));

        NativeQueryBuilder builder = NativeQuery.builder()
                .withQuery(query)
                .withPageable(pageable);
        // 키워드가 없으면 관련도 점수가 무의미하므로 최신순으로 정렬해 QueryDSL 목록과 일관되게 한다.
        if (!StringUtils.hasText(keyword)) {
            builder.withSort(sort -> sort.field(f -> f.field("createdAt").order(SortOrder.Desc)));
        }

        SearchHits<PerformanceDocument> hits =
                elasticsearchOperations.search(builder.build(), PerformanceDocument.class);

        List<PerformanceResponse> content = hits.stream()
                .map(hit -> hit.getContent().toResponse())
                .toList();
        return new PageImpl<>(content, pageable, hits.getTotalHits());
    }

    private Page<PerformanceResponse> searchViaPostgres(
            String keyword, PerformanceGenre genre, PerformanceRegion region, Pageable pageable) {
        return performanceRepository.searchByKeyword(keyword, genre, region, pageable)
                .map(PerformanceResponse::from);
    }
}
