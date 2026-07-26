package io.github.team404.tikitaka.performanceseat.repository;

import io.github.team404.tikitaka.performanceseat.entity.PerformanceSchedule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PerformanceScheduleRepository extends JpaRepository<PerformanceSchedule, Long> {

    List<PerformanceSchedule> findAllByPerformanceId(Long performanceId);

    void deleteAllByPerformanceId(Long performanceId);
}
