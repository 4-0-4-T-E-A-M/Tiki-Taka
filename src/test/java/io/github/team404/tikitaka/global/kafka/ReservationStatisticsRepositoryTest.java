package io.github.team404.tikitaka.global.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.team404.tikitaka.booking.entity.ReservationStatus;
import io.github.team404.tikitaka.global.kafka.entity.ReservationStatistics;
import io.github.team404.tikitaka.global.kafka.repository.ReservationStatisticsRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class ReservationStatisticsRepositoryTest {

    private static final Long SCHEDULE_ID = 10L;
    private static final LocalDateTime INITIAL_UPDATED_AT =
            LocalDateTime.of(2026, 8, 21, 11, 0);

    @Autowired
    private ReservationStatisticsRepository reservationStatisticsRepository;

    @Test
    void 예약_상태별_통계를_저장하고_조회할_수_있다() {
        ReservationStatistics statistics = createStatistics(
                SCHEDULE_ID,
                1L,
                2L,
                3L,
                4L,
                5L,
                INITIAL_UPDATED_AT
        );

        reservationStatisticsRepository.saveAndFlush(statistics);

        ReservationStatistics foundStatistics =
                reservationStatisticsRepository.findById(SCHEDULE_ID).orElseThrow();

        assertThat(foundStatistics.getScheduleId()).isEqualTo(SCHEDULE_ID);
        assertThat(foundStatistics.getPendingPaymentCount()).isEqualTo(1L);
        assertThat(foundStatistics.getConfirmedCount()).isEqualTo(2L);
        assertThat(foundStatistics.getFailedCount()).isEqualTo(3L);
        assertThat(foundStatistics.getExpiredCount()).isEqualTo(4L);
        assertThat(foundStatistics.getCanceledCount()).isEqualTo(5L);
        assertThat(foundStatistics.getUpdatedAt()).isEqualTo(INITIAL_UPDATED_AT);
    }

    @Test
    void scheduleId로_예약_통계를_조회할_수_있다() {
        reservationStatisticsRepository.saveAndFlush(
                createStatistics(SCHEDULE_ID, 0L, 152L, 0L, 0L, 0L, INITIAL_UPDATED_AT)
        );

        assertThat(reservationStatisticsRepository.findById(SCHEDULE_ID)).isPresent();
        assertThat(reservationStatisticsRepository.findById(999L)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(ReservationStatus.class)
    void 예약_상태에_맞는_통계를_증가시키고_갱신_시간을_변경할_수_있다(ReservationStatus status) {
        ReservationStatistics statistics = createStatistics(
                SCHEDULE_ID,
                0L,
                0L,
                0L,
                0L,
                0L,
                INITIAL_UPDATED_AT
        );
        LocalDateTime updatedAt = LocalDateTime.of(2026, 8, 21, 11, 1);

        statistics.increase(status, updatedAt);

        assertThat(statistics.getPendingPaymentCount())
                .isEqualTo(status == ReservationStatus.PENDING_PAYMENT ? 1L : 0L);
        assertThat(statistics.getConfirmedCount())
                .isEqualTo(status == ReservationStatus.CONFIRMED ? 1L : 0L);
        assertThat(statistics.getFailedCount())
                .isEqualTo(status == ReservationStatus.FAILED ? 1L : 0L);
        assertThat(statistics.getExpiredCount())
                .isEqualTo(status == ReservationStatus.EXPIRED ? 1L : 0L);
        assertThat(statistics.getCanceledCount())
                .isEqualTo(status == ReservationStatus.CANCELED ? 1L : 0L);
        assertThat(statistics.getUpdatedAt()).isEqualTo(updatedAt);
    }

    private ReservationStatistics createStatistics(
            Long scheduleId,
            Long pendingPaymentCount,
            Long confirmedCount,
            Long failedCount,
            Long expiredCount,
            Long canceledCount,
            LocalDateTime updatedAt
    ) {
        return ReservationStatistics.builder()
                .scheduleId(scheduleId)
                .pendingPaymentCount(pendingPaymentCount)
                .confirmedCount(confirmedCount)
                .failedCount(failedCount)
                .expiredCount(expiredCount)
                .canceledCount(canceledCount)
                .updatedAt(updatedAt)
                .build();
    }
}
