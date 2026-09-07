package io.github.team404.tikitaka.global.kafka;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import io.github.team404.tikitaka.booking.entity.ReservationStatus;
import io.github.team404.tikitaka.global.kafka.entity.ReservationStatistics;
import io.github.team404.tikitaka.global.kafka.event.ReservationEvent;
import io.github.team404.tikitaka.global.kafka.repository.ProcessedEventRepository;
import io.github.team404.tikitaka.global.kafka.repository.ReservationStatisticsRepository;
import io.github.team404.tikitaka.global.kafka.service.ReservationStatisticsService;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
class ReservationStatisticsServiceRollbackTest {

    private static final UUID EVENT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000099");

    @Autowired
    private ReservationStatisticsService reservationStatisticsService;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @MockitoBean
    private ReservationStatisticsRepository reservationStatisticsRepository;

    @Test
    void 통계_저장에_실패하면_ProcessedEvent도_롤백된다() {
        when(reservationStatisticsRepository.findById(10L)).thenReturn(Optional.empty());
        doThrow(new IllegalStateException("statistics save failed"))
                .when(reservationStatisticsRepository).save(any(ReservationStatistics.class));

        assertThatThrownBy(() -> reservationStatisticsService.process(event()))
                .isInstanceOf(IllegalStateException.class);

        assertThat(processedEventRepository.existsById(EVENT_ID)).isFalse();
    }

    private ReservationEvent event() {
        return new ReservationEvent(
                EVENT_ID,
                1L,
                2L,
                10L,
                ReservationStatus.CONFIRMED,
                LocalDateTime.of(2026, 8, 21, 12, 0)
        );
    }
}
