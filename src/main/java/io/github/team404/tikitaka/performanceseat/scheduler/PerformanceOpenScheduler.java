package io.github.team404.tikitaka.performanceseat.scheduler;

import io.github.team404.tikitaka.performanceseat.service.PerformanceScheduleOpenService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 오픈 시각이 지난 회차를 주기적으로 열어주는 트리거. 실제 전환 로직은 PerformanceScheduleOpenService.
// 다중 인스턴스에서 여러 스케줄러가 같은 회차를 동시에 집어도 PerformanceSchedule.open()이 멱등하다
// (이미 OPEN이면 no-op). 리더 선출·분산 락은 7주차 스케줄러 안정성 이슈 범위.
@Component
@RequiredArgsConstructor
public class PerformanceOpenScheduler {

    private final PerformanceScheduleOpenService performanceScheduleOpenService;

    @Scheduled(fixedDelayString = "${performance.open.poll-interval-ms:1000}")
    public void openDueSchedules() {
        performanceScheduleOpenService.openDueSchedules();
    }
}
