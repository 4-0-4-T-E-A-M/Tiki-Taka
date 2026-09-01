package io.github.team404.tikitaka.performanceseat.service;

import io.github.team404.tikitaka.performanceseat.entity.PerformanceSchedule;
import io.github.team404.tikitaka.performanceseat.entity.ScheduleStatus;
import io.github.team404.tikitaka.performanceseat.repository.PerformanceScheduleRepository;
import io.github.team404.tikitaka.performanceseat.repository.WaitingQueueRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 오픈 시각이 지난 회차를 SCHEDULED → OPEN으로 전환하고, 해당 회차의 대기열 입장 허용을 활성화한다.
// 트리거(주기)는 PerformanceOpenScheduler가 담당하고, 이 서비스는 한 번의 전환 배치를 처리한다.
@Service
@RequiredArgsConstructor
public class PerformanceScheduleOpenService {

    private static final Logger log = LoggerFactory.getLogger(PerformanceScheduleOpenService.class);

    private final PerformanceScheduleRepository scheduleRepository;
    private final WaitingQueueRepository waitingQueueRepository;

    // 오픈 시점에 입장을 허용할 초기 인원. 점진적 증가(ramp-up)는 7주차 안정성 이슈로 미룬다.
    @Value("${performance.open.initial-admit-count:100}")
    private long initialAdmitCount;

    // 회차 상태 전환은 트랜잭션 안에서 더티 체킹으로 반영된다. 대기열 입장 허용 인원 설정(Redis)은
    // 트랜잭션 밖이라 커밋 실패 시 키가 남을 수 있으나, SETNX라 다음 주기 재시도가 값을 덮지 않는다.
    @Transactional
    public int openDueSchedules() {
        List<PerformanceSchedule> due = scheduleRepository
                .findAllByStatusAndOpenAtLessThanEqual(ScheduleStatus.SCHEDULED, LocalDateTime.now());

        int opened = 0;
        for (PerformanceSchedule schedule : due) {
            if (schedule.open()) {
                waitingQueueRepository.initAdmitCount(schedule.getId(), initialAdmitCount);
                opened++;
                log.info("공연 회차 오픈: scheduleId={}, initialAdmitCount={}", schedule.getId(), initialAdmitCount);
            }
        }
        return opened;
    }
}
