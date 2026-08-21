package io.github.team404.tikitaka.global.kafka.service;

import io.github.team404.tikitaka.global.kafka.entity.ProcessedEvent;
import io.github.team404.tikitaka.global.kafka.entity.ReservationStatistics;
import io.github.team404.tikitaka.global.kafka.event.ReservationEvent;
import io.github.team404.tikitaka.global.kafka.repository.ProcessedEventRepository;
import io.github.team404.tikitaka.global.kafka.repository.ReservationStatisticsRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReservationStatisticsService {

    private final ProcessedEventRepository processedEventRepository;
    private final ReservationStatisticsRepository reservationStatisticsRepository;

    @Transactional
    public void process(ReservationEvent event) {
        if (processedEventRepository.existsById(event.eventId())) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        processedEventRepository.saveAndFlush(ProcessedEvent.builder()
                .eventId(event.eventId())
                .processedAt(now)
                .build());

        ReservationStatistics statistics = reservationStatisticsRepository.findById(event.scheduleId())
                .orElseGet(() -> ReservationStatistics.builder()
                        .scheduleId(event.scheduleId())
                        .pendingPaymentCount(0L)
                        .confirmedCount(0L)
                        .failedCount(0L)
                        .expiredCount(0L)
                        .canceledCount(0L)
                        .updatedAt(now)
                        .build());

        statistics.increase(event.status(), now);
        reservationStatisticsRepository.save(statistics);
    }
}
