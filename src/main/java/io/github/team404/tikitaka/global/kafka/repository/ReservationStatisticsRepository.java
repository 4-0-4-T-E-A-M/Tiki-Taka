package io.github.team404.tikitaka.global.kafka.repository;

import io.github.team404.tikitaka.global.kafka.entity.ReservationStatistics;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationStatisticsRepository extends JpaRepository<ReservationStatistics, Long> {
}
