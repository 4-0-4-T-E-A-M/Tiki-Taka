package io.github.team404.tikitaka.global.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.team404.tikitaka.global.kafka.entity.ReservationStatistics;
import io.github.team404.tikitaka.global.kafka.repository.ReservationStatisticsRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
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
    void 예약_통계를_저장하고_조회할_수_있다() {
        ReservationStatistics statistics = createStatistics(
                SCHEDULE_ID,
                1L,
                INITIAL_UPDATED_AT
        );

        reservationStatisticsRepository.saveAndFlush(statistics);

        ReservationStatistics foundStatistics =
                reservationStatisticsRepository.findById(SCHEDULE_ID).orElseThrow();

        assertThat(foundStatistics.getScheduleId()).isEqualTo(SCHEDULE_ID);
        assertThat(foundStatistics.getConfirmedCount()).isEqualTo(1L);
        assertThat(foundStatistics.getUpdatedAt()).isEqualTo(INITIAL_UPDATED_AT);
    }

    @Test
    void scheduleId로_예약_통계를_조회할_수_있다() {
        reservationStatisticsRepository.saveAndFlush(
                createStatistics(SCHEDULE_ID, 152L, INITIAL_UPDATED_AT)
        );

        assertThat(reservationStatisticsRepository.findById(SCHEDULE_ID)).isPresent();
        assertThat(reservationStatisticsRepository.findById(999L)).isEmpty();
    }

    @Test
    void 확정_예약_건수를_증가시키고_갱신_시간을_변경할_수_있다() {
        ReservationStatistics statistics = createStatistics(
                SCHEDULE_ID,
                1L,
                INITIAL_UPDATED_AT
        );
        reservationStatisticsRepository.saveAndFlush(statistics);
        LocalDateTime updatedAt = LocalDateTime.of(2026, 8, 21, 11, 1);

        statistics.increaseConfirmedCount(updatedAt);
        reservationStatisticsRepository.saveAndFlush(statistics);

        ReservationStatistics foundStatistics =
                reservationStatisticsRepository.findById(SCHEDULE_ID).orElseThrow();
        assertThat(foundStatistics.getConfirmedCount()).isEqualTo(2L);
        assertThat(foundStatistics.getUpdatedAt()).isEqualTo(updatedAt);
    }

    private ReservationStatistics createStatistics(
            Long scheduleId,
            Long confirmedCount,
            LocalDateTime updatedAt
    ) {
        return ReservationStatistics.builder()
                .scheduleId(scheduleId)
                .confirmedCount(confirmedCount)
                .updatedAt(updatedAt)
                .build();
    }
}
