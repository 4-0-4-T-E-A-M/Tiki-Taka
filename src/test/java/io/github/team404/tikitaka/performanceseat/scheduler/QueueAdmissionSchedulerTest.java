package io.github.team404.tikitaka.performanceseat.scheduler;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.team404.tikitaka.performanceseat.entity.PerformanceSchedule;
import io.github.team404.tikitaka.performanceseat.entity.ScheduleStatus;
import io.github.team404.tikitaka.performanceseat.repository.PerformanceScheduleRepository;
import io.github.team404.tikitaka.performanceseat.service.WaitingQueueService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class QueueAdmissionSchedulerTest {

    @Mock
    private PerformanceScheduleRepository scheduleRepository;

    @Mock
    private WaitingQueueService waitingQueueService;

    @InjectMocks
    private QueueAdmissionScheduler scheduler;

    private PerformanceSchedule scheduleWith(Long id) {
        PerformanceSchedule schedule = PerformanceSchedule.builder()
                .performanceId(1L)
                .performanceDatetime(LocalDateTime.now().plusDays(7))
                .openAt(LocalDateTime.now().minusMinutes(1))
                .build();
        ReflectionTestUtils.setField(schedule, "id", id);
        return schedule;
    }

    @Test
    void 열린_회차마다_대기열_입장_처리를_호출한다() {
        when(scheduleRepository.findAllByStatus(ScheduleStatus.OPEN))
                .thenReturn(List.of(scheduleWith(10L), scheduleWith(20L)));

        scheduler.admitDueUsers();

        verify(waitingQueueService).admitDueUsers(10L);
        verify(waitingQueueService).admitDueUsers(20L);
    }

    @Test
    void 한_회차_처리가_실패해도_나머지_회차는_계속_처리한다() {
        when(scheduleRepository.findAllByStatus(ScheduleStatus.OPEN))
                .thenReturn(List.of(scheduleWith(10L), scheduleWith(20L)));
        when(waitingQueueService.admitDueUsers(10L)).thenThrow(new RuntimeException("redis 일시 오류"));

        scheduler.admitDueUsers();

        verify(waitingQueueService).admitDueUsers(20L);
    }

    @Test
    void 열린_회차가_없으면_아무것도_하지_않는다() {
        when(scheduleRepository.findAllByStatus(ScheduleStatus.OPEN)).thenReturn(List.of());

        scheduler.admitDueUsers();

        verify(waitingQueueService, org.mockito.Mockito.never()).admitDueUsers(anyLong());
    }
}
