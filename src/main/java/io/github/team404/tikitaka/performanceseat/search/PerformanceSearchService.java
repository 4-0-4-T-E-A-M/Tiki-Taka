package io.github.team404.tikitaka.performanceseat.search;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import io.github.team404.tikitaka.performanceseat.dto.PerformanceResponse;
import io.github.team404.tikitaka.performanceseat.entity.PerformanceGenre;
import io.github.team404.tikitaka.performanceseat.entity.PerformanceRegion;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

// 공연 키워드 검색 (Elasticsearch 경로). 구조화 필터만 있는 조회는 QueryDSL 경로가 담당한다
// (docs/tradeoffs/search/queryDSL-vs-es-role-split.md). ES 장애 시 PostgreSQL 폴백은 #93 범위.
@Service
@RequiredArgsConstructor
public class PerformanceSearchService {

    private final ElasticsearchOperations elasticsearchOperations;

    public Page<PerformanceResponse> search(
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
            builder.withSort(sort -> sort.field(f -> f.field("createdAt").order(
                    co.elastic.clients.elasticsearch._types.SortOrder.Desc)));
        }

        SearchHits<PerformanceDocument> hits =
                elasticsearchOperations.search(builder.build(), PerformanceDocument.class);

        List<PerformanceResponse> content = hits.stream()
                .map(hit -> hit.getContent().toResponse())
                .toList();
        return new PageImpl<>(content, pageable, hits.getTotalHits());
    }
}
