package io.github.team404.tikitaka.performanceseat.scheduler;

import io.github.team404.tikitaka.performanceseat.entity.PerformanceSchedule;
import io.github.team404.tikitaka.performanceseat.entity.ScheduleStatus;
import io.github.team404.tikitaka.performanceseat.repository.PerformanceScheduleRepository;
import io.github.team404.tikitaka.performanceseat.service.WaitingQueueService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 열려 있는 회차마다 대기열 앞에서부터 사용자를 통과(admitted)시키는 주기 작업(#102).
// 통과 인원 목표(admit-count)는 공연 오픈 스케줄러(#75)가 오픈 시점에 설정한다.
// 한 회차의 처리 실패가 다른 회차를 막지 않도록 회차 단위로 예외를 삼킨다.
@Component
@RequiredArgsConstructor
public class QueueAdmissionScheduler {

    private static final Logger log = LoggerFactory.getLogger(QueueAdmissionScheduler.class);

    private final PerformanceScheduleRepository scheduleRepository;
    private final WaitingQueueService waitingQueueService;

    @Scheduled(fixedDelayString = "${queue.admission.poll-interval-ms:1000}")
    public void admitDueUsers() {
        for (PerformanceSchedule schedule : scheduleRepository.findAllByStatus(ScheduleStatus.OPEN)) {
            try {
                int admitted = waitingQueueService.admitDueUsers(schedule.getId());
                if (admitted > 0) {
                    log.info("대기열 입장 처리: scheduleId={}, admitted={}", schedule.getId(), admitted);
                }
            } catch (RuntimeException e) {
                log.warn("대기열 입장 처리 실패: scheduleId={}", schedule.getId(), e);
            }
        }
    }
}
