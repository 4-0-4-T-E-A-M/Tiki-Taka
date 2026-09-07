package io.github.team404.tikitaka.performanceseat.search;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.team404.tikitaka.performanceseat.entity.Performance;
import io.github.team404.tikitaka.performanceseat.entity.PerformanceGenre;
import io.github.team404.tikitaka.performanceseat.entity.PerformanceRegion;
import io.github.team404.tikitaka.performanceseat.repository.PerformanceRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PerformanceSearchIndexerTest {

    @Mock
    private PerformanceRepository performanceRepository;

    @Mock
    private PerformanceSearchRepository searchRepository;

    @Mock
    private ElasticsearchOperations elasticsearchOperations;

    @InjectMocks
    private PerformanceSearchIndexer indexer;

    private Performance performanceOf(long id) {
        Performance performance = Performance.builder()
                .title("아이유 콘서트")
                .artist("아이유")
                .venueName("체조경기장")
                .region(PerformanceRegion.SEOUL)
                .genre(PerformanceGenre.CONCERT)
                .build();
        ReflectionTestUtils.setField(performance, "id", id);
        return performance;
    }

    @Test
    void UPSERT는_PostgreSQL_최신값을_다시_읽어_색인한다() {
        when(performanceRepository.findById(1L)).thenReturn(Optional.of(performanceOf(1L)));

        indexer.indexById(1L);

        verify(searchRepository).save(any(PerformanceDocument.class));
    }

    @Test
    void UPSERT인데_이미_삭제됐으면_색인에서도_지운다() {
        when(performanceRepository.findById(1L)).thenReturn(Optional.empty());

        indexer.indexById(1L);

        verify(searchRepository).deleteById("1");
        verify(searchRepository, never()).save(any());
    }

    @Test
    void DELETE는_id로_색인_문서를_삭제한다() {
        indexer.deleteById(42L);

        verify(searchRepository).deleteById("42");
    }
}
