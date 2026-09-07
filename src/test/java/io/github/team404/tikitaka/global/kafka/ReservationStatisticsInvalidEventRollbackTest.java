package io.github.team404.tikitaka.global.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.team404.tikitaka.booking.entity.ReservationStatus;
import io.github.team404.tikitaka.global.kafka.event.ReservationEvent;
import io.github.team404.tikitaka.global.kafka.repository.ProcessedEventRepository;
import io.github.team404.tikitaka.global.kafka.repository.ReservationStatisticsRepository;
import io.github.team404.tikitaka.global.kafka.service.ReservationStatisticsService;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.InvalidDataAccessApiUsageException;

@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
class ReservationStatisticsInvalidEventRollbackTest {

    private static final UUID EVENT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000100");

    @Autowired
    private ReservationStatisticsService reservationStatisticsService;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private ReservationStatisticsRepository reservationStatisticsRepository;

    @Test
    void scheduleId가_없는_이벤트는_전체_처리를_롤백한다() {
        ReservationEvent invalidEvent = new ReservationEvent(
                EVENT_ID,
                1L,
                2L,
                null,
                ReservationStatus.CONFIRMED,
                LocalDateTime.of(2026, 8, 21, 12, 0)
        );

        assertThatThrownBy(() -> reservationStatisticsService.process(invalidEvent))
                .isInstanceOf(InvalidDataAccessApiUsageException.class);

        assertThat(processedEventRepository.existsById(EVENT_ID)).isFalse();
        assertThat(reservationStatisticsRepository.count()).isZero();
    }
}
