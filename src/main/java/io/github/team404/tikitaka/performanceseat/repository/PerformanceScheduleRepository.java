package io.github.team404.tikitaka.performanceseat.repository;

import io.github.team404.tikitaka.performanceseat.entity.PerformanceSchedule;
import io.github.team404.tikitaka.performanceseat.entity.ScheduleStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PerformanceScheduleRepository extends JpaRepository<PerformanceSchedule, Long> {

    List<PerformanceSchedule> findAllByPerformanceId(Long performanceId);

    void deleteAllByPerformanceId(Long performanceId);

    // 공연 오픈 스케줄러가 매 주기 조회하는 "열어야 할 회차" — 아직 SCHEDULED이면서 오픈 시각이 지난 것.
    // status 인덱스로 SCHEDULED만 좁힌 뒤 openAt을 필터한다(대개 0건). 규모가 커지면 (status, open_at)
    // 복합 인덱스를 검토하되, 이번 이슈에서는 인덱스 설계(#73)를 넘어서지 않는다.
    List<PerformanceSchedule> findAllByStatusAndOpenAtLessThanEqual(ScheduleStatus status, LocalDateTime now);

    // 대기열 입장 스케줄러(#102)가 매 주기 순회하는 "현재 열려 있는 회차".
    List<PerformanceSchedule> findAllByStatus(ScheduleStatus status);
}
