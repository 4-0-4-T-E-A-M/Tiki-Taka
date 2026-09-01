package io.github.team404.tikitaka.performanceseat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.team404.tikitaka.booking.repository.ReservationRepository;
import io.github.team404.tikitaka.performanceseat.dto.PerformanceCreateRequest;
import io.github.team404.tikitaka.performanceseat.dto.PerformanceDetailResponse;
import io.github.team404.tikitaka.performanceseat.dto.PerformanceResponse;
import io.github.team404.tikitaka.performanceseat.dto.PerformanceUpdateRequest;
import io.github.team404.tikitaka.performanceseat.dto.ScheduleCreateRequest;
import io.github.team404.tikitaka.performanceseat.dto.SectionCreateRequest;
import io.github.team404.tikitaka.performanceseat.dto.SeatRowRequest;
import io.github.team404.tikitaka.performanceseat.entity.Performance;
import io.github.team404.tikitaka.performanceseat.entity.PerformanceGenre;
import io.github.team404.tikitaka.performanceseat.entity.PerformanceRegion;
import io.github.team404.tikitaka.performanceseat.entity.PerformanceSchedule;
import io.github.team404.tikitaka.performanceseat.entity.Section;
import io.github.team404.tikitaka.performanceseat.entity.SeatGrade;
import io.github.team404.tikitaka.performanceseat.repository.PerformanceCacheRepository;
import io.github.team404.tikitaka.performanceseat.repository.PerformanceRepository;
import io.github.team404.tikitaka.performanceseat.repository.PerformanceScheduleRepository;
import io.github.team404.tikitaka.performanceseat.repository.SeatRepository;
import io.github.team404.tikitaka.performanceseat.repository.SectionRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PerformanceServiceTest {

    @Mock
    private PerformanceRepository performanceRepository;

    @Mock
    private PerformanceScheduleRepository performanceScheduleRepository;

    @Mock
    private SectionRepository sectionRepository;

    @Mock
    private SeatRepository seatRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private PerformanceCacheRepository performanceCacheRepository;

    @InjectMocks
    private PerformanceService performanceService;

    @Test
    void 공연_등록시_회차_구역_좌석이_요청대로_생성된다() {
        // given
        SeatRowRequest rowA = new SeatRowRequest("A", 2, SeatGrade.VIP, 150_000);
        SectionCreateRequest section = new SectionCreateRequest("1층 A구역", List.of(rowA));
        ScheduleCreateRequest schedule = new ScheduleCreateRequest(
                LocalDateTime.now().plusDays(7), LocalDateTime.now().plusDays(1), List.of(section));
        PerformanceCreateRequest request = new PerformanceCreateRequest(
                "첫 콘서트", "아이유", "체조경기장", PerformanceRegion.SEOUL, PerformanceGenre.CONCERT,
                "설명", "poster.png", List.of(schedule));

        // when
        Performance performance = performanceService.createPerformance(request);

        // then
        assertThat(performance.getTitle()).isEqualTo("첫 콘서트");
        verify(performanceRepository, times(1)).save(performance);
        verify(performanceScheduleRepository, times(1)).save(any(PerformanceSchedule.class));
        verify(sectionRepository, times(1)).save(any(Section.class));
        verify(seatRepository, times(2)).save(any());
    }

    @Test
    void 공연_수정시_상세_캐시를_무효화한다() {
        // given
        Performance performance = performanceOf();
        PerformanceUpdateRequest request = new PerformanceUpdateRequest(
                "수정된 콘서트", "아이유", "체조경기장", PerformanceRegion.SEOUL, PerformanceGenre.CONCERT,
                "수정된 설명", "poster2.png");
        when(performanceRepository.findById(1L)).thenReturn(Optional.of(performance));

        // when
        performanceService.updatePerformance(1L, request);

        // then
        assertThat(performance.getTitle()).isEqualTo("수정된 콘서트");
        verify(performanceCacheRepository, times(1)).evictDetail(1L);
    }

    @Test
    void 삭제시_예매가_존재하면_예외가_발생한다() {
        // given
        Performance performance = performanceOf();
        PerformanceSchedule schedule = scheduleMockOf(10L);
        when(performanceRepository.findById(1L)).thenReturn(Optional.of(performance));
        when(performanceScheduleRepository.findAllByPerformanceId(1L)).thenReturn(List.of(schedule));
        when(reservationRepository.existsByScheduleIdIn(List.of(10L))).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> performanceService.deletePerformance(1L))
                .isInstanceOf(ResponseStatusException.class);
        verify(performanceRepository, never()).delete(any());
        verify(performanceCacheRepository, never()).evictDetail(any());
    }

    @Test
    void 삭제시_예매가_없으면_좌석_구역_회차_공연_순으로_삭제된다() {
        // given
        Performance performance = performanceOf();
        PerformanceSchedule schedule = scheduleMockOf(10L);
        Section section = sectionMockOf(100L);
        when(performanceRepository.findById(1L)).thenReturn(Optional.of(performance));
        when(performanceScheduleRepository.findAllByPerformanceId(1L)).thenReturn(List.of(schedule));
        when(reservationRepository.existsByScheduleIdIn(List.of(10L))).thenReturn(false);
        when(sectionRepository.findAllByScheduleIdIn(List.of(10L))).thenReturn(List.of(section));

        // when
        performanceService.deletePerformance(1L);

        // then
        verify(seatRepository, times(1)).deleteAllBySectionIdIn(List.of(100L));
        verify(sectionRepository, times(1)).deleteAllByScheduleIdIn(List.of(10L));
        verify(performanceScheduleRepository, times(1)).deleteAllByPerformanceId(1L);
        verify(performanceRepository, times(1)).delete(performance);
        verify(performanceCacheRepository, times(1)).evictDetail(1L);
    }

    @Test
    void 상세조회시_공연과_회차_목록을_함께_반환한다() {
        // given
        Performance performance = performanceOf();
        PerformanceSchedule schedule = mock(PerformanceSchedule.class);
        when(performanceRepository.findById(1L)).thenReturn(Optional.of(performance));
        when(performanceScheduleRepository.findAllByPerformanceId(1L)).thenReturn(List.of(schedule));

        // when
        Performance found = performanceService.getPerformance(1L);
        List<PerformanceSchedule> schedules = performanceService.getSchedules(1L);

        // then
        assertThat(found).isEqualTo(performance);
        assertThat(schedules).hasSize(1);
    }

    @Test
    void 공연_상세조회시_캐시가_히트되면_DB를_조회하지_않는다() {
        // given
        PerformanceDetailResponse cached = new PerformanceDetailResponse(
                new PerformanceResponse(1L, "첫 콘서트", "아이유", "체조경기장",
                        PerformanceRegion.SEOUL, PerformanceGenre.CONCERT, null, null, null),
                List.of());
        when(performanceCacheRepository.findDetail(1L)).thenReturn(Optional.of(cached));

        // when
        PerformanceDetailResponse result = performanceService.getPerformanceDetail(1L);

        // then
        assertThat(result).isEqualTo(cached);
        verify(performanceRepository, never()).findById(any());
        verify(performanceCacheRepository, never()).saveDetail(any(), any());
    }

    @Test
    void 공연_상세조회시_캐시가_미스되면_DB조회후_캐시를_채운다() {
        // given
        Performance performance = performanceOf();
        PerformanceSchedule schedule = mock(PerformanceSchedule.class);
        when(performanceCacheRepository.findDetail(1L)).thenReturn(Optional.empty());
        when(performanceRepository.findById(1L)).thenReturn(Optional.of(performance));
        when(performanceScheduleRepository.findAllByPerformanceId(1L)).thenReturn(List.of(schedule));

        // when
        PerformanceDetailResponse result = performanceService.getPerformanceDetail(1L);

        // then
        assertThat(result.performance().title()).isEqualTo("첫 콘서트");
        assertThat(result.schedules()).hasSize(1);

        ArgumentCaptor<PerformanceDetailResponse> captor = ArgumentCaptor.forClass(PerformanceDetailResponse.class);
        verify(performanceCacheRepository, times(1)).saveDetail(any(), captor.capture());
        assertThat(captor.getValue()).isEqualTo(result);
    }

    private Performance performanceOf() {
        return Performance.builder()
                .title("첫 콘서트")
                .artist("아이유")
                .venueName("체조경기장")
                .region(PerformanceRegion.SEOUL)
                .genre(PerformanceGenre.CONCERT)
                .build();
    }

    // 실제 저장 없이 id가 필요한 캐스케이드 삭제 검증용 (JpaRepository.save가 mock이라 IDENTITY 채번이 일어나지 않음)
    private PerformanceSchedule scheduleMockOf(Long id) {
        PerformanceSchedule schedule = mock(PerformanceSchedule.class);
        when(schedule.getId()).thenReturn(id);
        return schedule;
    }

    private Section sectionMockOf(Long id) {
        Section section = mock(Section.class);
        when(section.getId()).thenReturn(id);
        return section;
    }
}
