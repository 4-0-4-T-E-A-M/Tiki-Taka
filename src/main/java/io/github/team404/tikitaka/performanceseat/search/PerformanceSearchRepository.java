package io.github.team404.tikitaka.performanceseat.search;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

// 색인 저장/삭제용. 검색 쿼리(multi_match + 필터)는 ElasticsearchOperations로 직접 조립한다
// (PerformanceSearchService).
public interface PerformanceSearchRepository extends ElasticsearchRepository<PerformanceDocument, String> {
}
