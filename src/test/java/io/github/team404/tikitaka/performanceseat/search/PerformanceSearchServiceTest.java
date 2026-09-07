package io.github.team404.tikitaka.performanceseat.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.team404.tikitaka.performanceseat.dto.PerformanceResponse;
import io.github.team404.tikitaka.performanceseat.dto.PerformanceSearchResponse;
import io.github.team404.tikitaka.performanceseat.entity.Performance;
import io.github.team404.tikitaka.performanceseat.entity.PerformanceGenre;
import io.github.team404.tikitaka.performanceseat.entity.PerformanceRegion;
import io.github.team404.tikitaka.performanceseat.repository.PerformanceRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PerformanceSearchServiceTest {

    @Mock
    private ElasticsearchOperations elasticsearchOperations;

    @Mock
    private PerformanceRepository performanceRepository;

    @InjectMocks
    private PerformanceSearchService searchService;

    private final Pageable pageable = PageRequest.of(0, 10);

    private Performance performanceOf(long id, String title, String artist) {
        Performance performance = Performance.builder()
                .title(title)
                .artist(artist)
                .venueName("체조경기장")
                .region(PerformanceRegion.SEOUL)
                .genre(PerformanceGenre.CONCERT)
                .build();
        ReflectionTestUtils.setField(performance, "id", id);
        return performance;
    }

    @Test
    void ES가_정상이면_ES_결과를_degraded_false로_반환한다() {
        SearchHits<PerformanceDocument> hits = emptyHits();
        when(elasticsearchOperations.search(any(Query.class), eq(PerformanceDocument.class))).thenReturn(hits);

        PerformanceSearchResponse result = searchService.search("아이유", null, null, pageable);

        assertThat(result.degraded()).isFalse();
        verify(performanceRepository, never()).searchByKeyword(any(), any(), any(), any());
    }

    @Test
    void ES가_예외를_던지면_PostgreSQL_폴백_결과를_degraded_true로_반환한다() {
        when(elasticsearchOperations.search(any(Query.class), eq(PerformanceDocument.class)))
                .thenThrow(new RuntimeException("connection refused"));
        Page<Performance> pgResult = new PageImpl<>(
                List.of(performanceOf(1L, "아이유 콘서트", "아이유")), pageable, 1);
        when(performanceRepository.searchByKeyword("아이유", null, null, pageable)).thenReturn(pgResult);

        PerformanceSearchResponse result = searchService.search("아이유", null, null, pageable);

        assertThat(result.degraded()).isTrue();
        assertThat(result.content()).extracting(PerformanceResponse::id).containsExactly(1L);
        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test
    void 폴백시에도_genre_region_필터와_페이지네이션_계약이_유지된다() {
        when(elasticsearchOperations.search(any(Query.class), eq(PerformanceDocument.class)))
                .thenThrow(new RuntimeException("es down"));
        Pageable page2 = PageRequest.of(2, 5);
        when(performanceRepository.searchByKeyword("뮤지컬", PerformanceGenre.MUSICAL, PerformanceRegion.SEOUL, page2))
                .thenReturn(new PageImpl<>(List.of(), page2, 12));

        PerformanceSearchResponse result = searchService.search(
                "뮤지컬", PerformanceGenre.MUSICAL, PerformanceRegion.SEOUL, page2);

        assertThat(result.degraded()).isTrue();
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.size()).isEqualTo(5);
        assertThat(result.totalElements()).isEqualTo(12);
        verify(performanceRepository).searchByKeyword("뮤지컬", PerformanceGenre.MUSICAL, PerformanceRegion.SEOUL, page2);
    }

    @SuppressWarnings("unchecked")
    private SearchHits<PerformanceDocument> emptyHits() {
        SearchHits<PerformanceDocument> hits = org.mockito.Mockito.mock(SearchHits.class);
        when(hits.stream()).thenReturn(java.util.stream.Stream.empty());
        when(hits.getTotalHits()).thenReturn(0L);
        return hits;
    }
}
