package io.github.team404.tikitaka.performanceseat.ranking;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.team404.tikitaka.booking.entity.ReservationStatus;
import io.github.team404.tikitaka.global.kafka.event.ReservationEvent;
import io.github.team404.tikitaka.performanceseat.entity.PerformanceSchedule;
import io.github.team404.tikitaka.performanceseat.repository.PerformanceScheduleRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReservationRankingConsumerTest {

    private static final UUID RANKING_EVENT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000201");

    @Mock
    private PopularPerformanceRankingService rankingService;

    @Mock
    private PerformanceScheduleRepository performanceScheduleRepository;

    @InjectMocks
    private ReservationRankingConsumer consumer;

    private ReservationEvent event(Long scheduleId, ReservationStatus status) {
        return new ReservationEvent(RANKING_EVENT_ID, 1L, 10L, scheduleId, status, LocalDateTime.now());
    }

    private PerformanceSchedule scheduleWithPerformanceId(long performanceId) {
        PerformanceSchedule schedule = PerformanceSchedule.builder()
                .performanceId(performanceId)
                .performanceDatetime(LocalDateTime.now().plusDays(7))
                .build();
        return schedule;
    }

    @Test
    void CONFIRMED_예매는_회차의_공연을_찾아_예매_점수를_반영한다() {
        when(performanceScheduleRepository.findById(100L))
                .thenReturn(Optional.of(scheduleWithPerformanceId(42L)));

        consumer.consume(event(100L, ReservationStatus.CONFIRMED));

        verify(rankingService).recordReservation(42L);
    }

    @Test
    void CONFIRMED가_아닌_예매는_무시한다() {
        consumer.consume(event(100L, ReservationStatus.PENDING_PAYMENT));

        verify(performanceScheduleRepository, never()).findById(org.mockito.ArgumentMatchers.any());
        verify(rankingService, never()).recordReservation(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 회차를_찾을_수_없으면_반영하지_않는다() {
        when(performanceScheduleRepository.findById(100L)).thenReturn(Optional.empty());

        consumer.consume(event(100L, ReservationStatus.CONFIRMED));

        verify(rankingService, never()).recordReservation(org.mockito.ArgumentMatchers.any());
    }
}
