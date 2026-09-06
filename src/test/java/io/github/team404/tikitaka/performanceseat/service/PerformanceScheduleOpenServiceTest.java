package io.github.team404.tikitaka.performanceseat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.team404.tikitaka.performanceseat.entity.PerformanceSchedule;
import io.github.team404.tikitaka.performanceseat.entity.ScheduleStatus;
import io.github.team404.tikitaka.performanceseat.repository.PerformanceScheduleRepository;
import io.github.team404.tikitaka.performanceseat.repository.WaitingQueueRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PerformanceScheduleOpenServiceTest {

    private static final long INITIAL_ADMIT_COUNT = 100L;

    @Mock
    private PerformanceScheduleRepository scheduleRepository;

    @Mock
    private WaitingQueueRepository waitingQueueRepository;

    @InjectMocks
    private PerformanceScheduleOpenService performanceScheduleOpenService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(performanceScheduleOpenService, "initialAdmitCount", INITIAL_ADMIT_COUNT);
    }

    private PerformanceSchedule scheduleWith(Long id, ScheduleStatus status) {
        PerformanceSchedule schedule = PerformanceSchedule.builder()
                .performanceId(1L)
                .performanceDatetime(LocalDateTime.now().plusDays(7))
                .openAt(LocalDateTime.now().minusMinutes(1))
                .build();
        ReflectionTestUtils.setField(schedule, "id", id);
        ReflectionTestUtils.setField(schedule, "status", status);
        return schedule;
    }

    @Test
    void 열_회차가_없으면_아무것도_하지_않는다() {
        when(scheduleRepository.findAllByStatusAndOpenAtLessThanEqual(eq(ScheduleStatus.SCHEDULED), any(LocalDateTime.class)))
                .thenReturn(List.of());

        int opened = performanceScheduleOpenService.openDueSchedules();

        assertThat(opened).isZero();
        verify(waitingQueueRepository, never()).initAdmitCount(anyLong(), anyLong());
    }

    @Test
    void 오픈_시각이_지난_회차를_열고_대기열_입장_허용_인원을_초기화한다() {
        PerformanceSchedule schedule = scheduleWith(10L, ScheduleStatus.SCHEDULED);
        when(scheduleRepository.findAllByStatusAndOpenAtLessThanEqual(eq(ScheduleStatus.SCHEDULED), any(LocalDateTime.class)))
                .thenReturn(List.of(schedule));

        int opened = performanceScheduleOpenService.openDueSchedules();

        assertThat(opened).isEqualTo(1);
        assertThat(schedule.getStatus()).isEqualTo(ScheduleStatus.OPEN);
        verify(waitingQueueRepository).initAdmitCount(10L, INITIAL_ADMIT_COUNT);
    }

    @Test
    void 이미_OPEN인_회차가_섞여있으면_입장_허용_인원을_다시_초기화하지_않는다() {
        PerformanceSchedule alreadyOpen = scheduleWith(10L, ScheduleStatus.OPEN);
        when(scheduleRepository.findAllByStatusAndOpenAtLessThanEqual(eq(ScheduleStatus.SCHEDULED), any(LocalDateTime.class)))
                .thenReturn(List.of(alreadyOpen));

        int opened = performanceScheduleOpenService.openDueSchedules();

        assertThat(opened).isZero();
        verify(waitingQueueRepository, never()).initAdmitCount(anyLong(), anyLong());
    }
}
