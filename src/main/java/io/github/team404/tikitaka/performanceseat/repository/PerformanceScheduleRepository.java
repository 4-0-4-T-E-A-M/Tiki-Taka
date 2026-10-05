package io.github.team404.tikitaka.performanceseat.repository;

import io.github.team404.tikitaka.performanceseat.entity.PerformanceSchedule;
import io.github.team404.tikitaka.performanceseat.entity.ScheduleStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PerformanceScheduleRepository extends JpaRepository<PerformanceSchedule, Long> {

    List<PerformanceSchedule> findAllByPerformanceId(Long performanceId);

    void deleteAllByPerformanceId(Long performanceId);

    // 공연 오픈 스케줄러가 매 주기 조회하는 "열어야 할 회차" — 아직 SCHEDULED이면서 오픈 시각이 지난 것.
    // status 인덱스로 SCHEDULED만 좁힌 뒤 openAt을 필터한다(대개 0건). 규모가 커지면 (status, open_at)
    // 복합 인덱스를 검토하되, 이번 이슈에서는 인덱스 설계(#73)를 넘어서지 않는다.
    List<PerformanceSchedule> findAllByStatusAndOpenAtLessThanEqual(ScheduleStatus status, LocalDateTime now);

    // 대기열 입장 스케줄러(#102)가 매 주기 순회하는 "현재 열려 있는 회차".
    List<PerformanceSchedule> findAllByStatus(ScheduleStatus status);

    // WHERE에 status = SCHEDULED 조건을 걸어 DB 행 잠금으로 "이번에 실제로 전환했는지"를 확정한다.
    // 다중 인스턴스/스레드가 같은 회차를 동시에 집어도, 먼저 커밋한 트랜잭션만 영향받은 행 1건을 받고
    // 나머지는 조건이 더 이상 맞지 않아 0건을 받는다 — 엔티티를 읽은 뒤 open()으로 메모리에서 상태를
    // 바꿔 더티 체킹에 맡기던 이전 방식은 두 트랜잭션이 같은 최종값(OPEN)을 각자 쓰기 때문에 데이터
    // 손상은 없었지만, 둘 다 "전환 성공"으로 오인해 오픈 로그·대기열 초기화 호출이 중복됐다(#117).
    @Modifying(clearAutomatically = true)
    @Query("UPDATE PerformanceSchedule p SET p.status = io.github.team404.tikitaka.performanceseat.entity.ScheduleStatus.OPEN "
            + "WHERE p.id = :id AND p.status = io.github.team404.tikitaka.performanceseat.entity.ScheduleStatus.SCHEDULED")
    int openIfScheduled(@Param("id") Long id);
}
