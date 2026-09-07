package io.github.team404.tikitaka.performanceseat.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.team404.tikitaka.performanceseat.dto.PerformanceResponse;
import io.github.team404.tikitaka.performanceseat.entity.Performance;
import io.github.team404.tikitaka.performanceseat.entity.PerformanceGenre;
import io.github.team404.tikitaka.performanceseat.entity.PerformanceRegion;
import io.github.team404.tikitaka.performanceseat.repository.PerformanceRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PopularPerformanceRankingServiceTest {

    @Mock
    private PopularPerformanceRankingRepository rankingRepository;

    @Mock
    private PerformanceRepository performanceRepository;

    @InjectMocks
    private PopularPerformanceRankingService rankingService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(rankingService, "viewWeight", 1.0);
        ReflectionTestUtils.setField(rankingService, "reservationWeight", 10.0);
    }

    private Performance performanceOf(long id) {
        Performance performance = Performance.builder()
                .title("공연 " + id)
                .artist("아티스트")
                .venueName("체조경기장")
                .region(PerformanceRegion.SEOUL)
                .genre(PerformanceGenre.CONCERT)
                .build();
        ReflectionTestUtils.setField(performance, "id", id);
        return performance;
    }

    @Test
    void 조회는_view_가중치로_점수를_더한다() {
        rankingService.recordView(7L);

        verify(rankingRepository).addScore(7L, 1.0);
    }

    @Test
    void 예매는_reservation_가중치로_점수를_더한다() {
        rankingService.recordReservation(7L);

        verify(rankingRepository).addScore(7L, 10.0);
    }

    @Test
    void 점수_반영_실패는_삼켜서_흐름을_깨지_않는다() {
        doThrow(new RuntimeException("redis down")).when(rankingRepository).addScore(anyLong(), anyDouble());

        rankingService.recordView(7L); // 예외 없이 리턴해야 한다
    }

    @Test
    void 상위_조회는_Redis_순서를_유지하고_존재하는_공연만_limit개_반환한다() {
        // Redis 랭킹: [3, 1, 99(삭제), 2] — 넉넉히(limit*3) 뽑아온다
        when(rankingRepository.topPerformanceIds(6)).thenReturn(List.of(3L, 1L, 99L, 2L));
        when(performanceRepository.findAllById(List.of(3L, 1L, 99L, 2L)))
                .thenReturn(List.of(performanceOf(1L), performanceOf(2L), performanceOf(3L)));

        List<PerformanceResponse> result = rankingService.topPerformances(2);

        // 99는 DB에 없어 제외, Redis 순서(3,1) 유지, limit=2
        assertThat(result).extracting(PerformanceResponse::id).containsExactly(3L, 1L);
    }

    @Test
    void 랭킹이_비어있으면_빈_목록을_반환한다() {
        when(rankingRepository.topPerformanceIds(30)).thenReturn(List.of());

        assertThat(rankingService.topPerformances(10)).isEmpty();
    }

    @Test
    void 랭킹_조회_실패시_빈_목록을_반환한다() {
        when(rankingRepository.topPerformanceIds(30)).thenThrow(new RuntimeException("redis down"));

        assertThat(rankingService.topPerformances(10)).isEmpty();
    }

    @Test
    void limit이_0이하면_Redis를_건드리지_않고_빈_목록() {
        assertThat(rankingService.topPerformances(0)).isEmpty();
    }
}
