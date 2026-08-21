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
    void 모든_예약_상태_이벤트를_상태별로_집계한다() {
        process(1, ReservationStatus.PENDING_PAYMENT);
        process(2, ReservationStatus.CONFIRMED);
        process(3, ReservationStatus.FAILED);
        process(4, ReservationStatus.EXPIRED);
        process(5, ReservationStatus.CANCELED);

        ReservationStatistics statistics =
                reservationStatisticsRepository.findById(SCHEDULE_ID).orElseThrow();
        assertThat(statistics.getPendingPaymentCount()).isEqualTo(1L);
        assertThat(statistics.getConfirmedCount()).isEqualTo(1L);
        assertThat(statistics.getFailedCount()).isEqualTo(1L);
        assertThat(statistics.getExpiredCount()).isEqualTo(1L);
        assertThat(statistics.getCanceledCount()).isEqualTo(1L);
        assertThat(processedEventRepository.count()).isEqualTo(5L);
    }

    @Test
    void 동일한_FAILED_eventId를_재처리해도_중복_집계하지_않는다() {
        ReservationEvent event = event(6, ReservationStatus.FAILED);

        reservationStatisticsService.process(event);
        reservationStatisticsService.process(event);

        ReservationStatistics statistics =
                reservationStatisticsRepository.findById(SCHEDULE_ID).orElseThrow();
        assertThat(statistics.getFailedCount()).isEqualTo(1L);
        assertThat(processedEventRepository.count()).isEqualTo(1L);
    }

    @Test
    void 서로_다른_FAILED_eventId는_각각_집계한다() {
        reservationStatisticsService.process(event(7, ReservationStatus.FAILED));
        reservationStatisticsService.process(event(8, ReservationStatus.FAILED));

        ReservationStatistics statistics =
                reservationStatisticsRepository.findById(SCHEDULE_ID).orElseThrow();
        assertThat(statistics.getFailedCount()).isEqualTo(2L);
        assertThat(processedEventRepository.count()).isEqualTo(2L);
    }

    private void process(int eventId, ReservationStatus status) {
        reservationStatisticsService.process(event(eventId, status));
    }

    private ReservationEvent event(int eventId, ReservationStatus status) {
        return new ReservationEvent(
                UUID.fromString("00000000-0000-0000-0000-0000000000" + String.format("%02d", eventId)),
                1L,
                2L,
                SCHEDULE_ID,
                status,
                LocalDateTime.of(2026, 8, 21, 12, 0)
        );
    }
}
