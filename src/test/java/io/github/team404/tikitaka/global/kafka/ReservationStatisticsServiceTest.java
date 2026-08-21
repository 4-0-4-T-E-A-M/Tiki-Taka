package io.github.team404.tikitaka.global.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.team404.tikitaka.booking.entity.ReservationStatus;
import io.github.team404.tikitaka.global.kafka.entity.ReservationStatistics;
import io.github.team404.tikitaka.global.kafka.event.ReservationEvent;
import io.github.team404.tikitaka.global.kafka.repository.ProcessedEventRepository;
import io.github.team404.tikitaka.global.kafka.repository.ReservationStatisticsRepository;
import io.github.team404.tikitaka.global.kafka.service.ReservationStatisticsService;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(ReservationStatisticsService.class)
class ReservationStatisticsServiceTest {

    private static final Long SCHEDULE_ID = 10L;

    @Autowired
    private ReservationStatisticsService reservationStatisticsService;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private ReservationStatisticsRepository reservationStatisticsRepository;

    @Test
    void 최초_CONFIRMED_이벤트를_처리하면_처리기록과_통계가_저장된다() {
        UUID eventId = eventId(1);

        reservationStatisticsService.process(confirmedEvent(eventId, SCHEDULE_ID));

        assertThat(processedEventRepository.existsById(eventId)).isTrue();
        ReservationStatistics statistics =
                reservationStatisticsRepository.findById(SCHEDULE_ID).orElseThrow();
        assertThat(statistics.getConfirmedCount()).isEqualTo(1L);
        assertThat(statistics.getUpdatedAt()).isNotNull();
    }

    @Test
    void 동일한_eventId를_재처리해도_통계가_중복_증가하지_않는다() {
        UUID eventId = eventId(2);
        ReservationEvent event = confirmedEvent(eventId, SCHEDULE_ID);

        reservationStatisticsService.process(event);
        reservationStatisticsService.process(event);

        assertThat(processedEventRepository.count()).isEqualTo(1L);
        assertThat(reservationStatisticsRepository.findById(SCHEDULE_ID).orElseThrow()
                .getConfirmedCount()).isEqualTo(1L);
    }

    @Test
    void 서로_다른_eventId가_같은_scheduleId로_들어오면_통계가_각각_증가한다() {
        reservationStatisticsService.process(confirmedEvent(eventId(3), SCHEDULE_ID));
        reservationStatisticsService.process(confirmedEvent(eventId(4), SCHEDULE_ID));

        assertThat(processedEventRepository.count()).isEqualTo(2L);
        assertThat(reservationStatisticsRepository.findById(SCHEDULE_ID).orElseThrow()
                .getConfirmedCount()).isEqualTo(2L);
    }

    @Test
    void CONFIRMED가_아닌_이벤트는_처리하지_않는다() {
        UUID eventId = eventId(5);
        ReservationEvent event = new ReservationEvent(
                eventId,
                1L,
                2L,
                SCHEDULE_ID,
                ReservationStatus.CANCELED,
                LocalDateTime.of(2026, 8, 21, 12, 0)
        );

        reservationStatisticsService.process(event);

        assertThat(processedEventRepository.existsById(eventId)).isFalse();
        assertThat(reservationStatisticsRepository.findById(SCHEDULE_ID)).isEmpty();
    }

    private ReservationEvent confirmedEvent(UUID eventId, Long scheduleId) {
        return new ReservationEvent(
                eventId,
                1L,
                2L,
                scheduleId,
                ReservationStatus.CONFIRMED,
                LocalDateTime.of(2026, 8, 21, 12, 0)
        );
    }

    private UUID eventId(int value) {
        return UUID.fromString("00000000-0000-0000-0000-00000000000" + value);
    }
}
