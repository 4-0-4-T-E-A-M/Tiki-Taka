package io.github.team404.tikitaka.performanceseat.scheduler;

import io.github.team404.tikitaka.performanceseat.service.PerformanceScheduleOpenService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 오픈 시각이 지난 회차를 주기적으로 열어주는 트리거. 실제 전환 로직은 PerformanceScheduleOpenService.
// 다중 인스턴스에서 여러 스케줄러가 같은 회차를 동시에 집어도 조건부 UPDATE(WHERE status = SCHEDULED)가
// DB 행 잠금으로 오직 한 트랜잭션만 전환에 성공하게 만든다(PerformanceScheduleRepository.openIfScheduled,
// #117 스케줄러 안정성 테스트로 검증). 리더 선출로 "한 인스턴스만 실행"하게 막는 대신, "모두 실행해도
// 결과가 멱등"한 방식을 택했다 — 별도 분산 락 컴포넌트 없이 기존 DB 트랜잭션만으로 충분하기 때문.
@Component
@RequiredArgsConstructor
public class PerformanceOpenScheduler {

    private final PerformanceScheduleOpenService performanceScheduleOpenService;

    @Scheduled(fixedDelayString = "${performance.open.poll-interval-ms:1000}")
    public void openDueSchedules() {
        performanceScheduleOpenService.openDueSchedules();
    }
}
