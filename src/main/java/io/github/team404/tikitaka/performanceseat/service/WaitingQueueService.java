package io.github.team404.tikitaka.performanceseat.service;

import io.github.team404.tikitaka.global.exception.BusinessException;
import io.github.team404.tikitaka.performanceseat.exception.QueueErrorCode;
import io.github.team404.tikitaka.performanceseat.repository.PerformanceScheduleRepository;
import io.github.team404.tikitaka.performanceseat.repository.WaitingQueueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 대기열 순번 관리 (회차 단위). score는 요청 시각 타임스탬프가 아니라 Redis INCR 원자 카운터로 발급한다 —
// 이 프로젝트는 "동시 다발적 봇 트래픽"을 그대로 재현하는 게 목적이라 밀리초 단위 타임스탬프 충돌이
// 실제로 발생할 수 있는 조건이고, 카운터는 INCR의 원자성만으로 순서를 보장할 수 있기 때문이다.
@Service
@RequiredArgsConstructor
public class WaitingQueueService {

    private final WaitingQueueRepository waitingQueueRepository;
    private final PerformanceScheduleRepository performanceScheduleRepository;

    // 대기열 진입. 이미 진입한 사용자가 다시 호출해도 기존 순번을 그대로 반환한다(재요청으로 순번이 밀리지 않음).
    // 이미 입장을 통과한(admitted) 사용자는 다시 큐에 넣지 않는다 — 넣으면 새 score를 받아 맨 뒤로 밀린다.
    public long enter(Long scheduleId, Long userId) {
        validateSchedule(scheduleId);
        if (!waitingQueueRepository.isAdmitted(scheduleId, userId)) {
            waitingQueueRepository.addIfAbsent(scheduleId, userId);
        }
        return getRank(scheduleId, userId);
    }

    // 대기 중이면 ZSET 순번, 이미 통과했으면 0(맨 앞으로 취급 — 실제 구분은 isAdmitted 플래그로).
    public long getRank(Long scheduleId, Long userId) {
        Long rank = waitingQueueRepository.rank(scheduleId, userId);
        if (rank != null) {
            return rank;
        }
        if (waitingQueueRepository.isAdmitted(scheduleId, userId)) {
            return 0;
        }
        throw new BusinessException(QueueErrorCode.QUEUE_ENTRY_NOT_FOUND);
    }

    public long size(Long scheduleId) {
        Long size = waitingQueueRepository.size(scheduleId);
        return size == null ? 0 : size;
    }

    // 입장 허용 여부 — 대기열을 통과해 admitted 집합에 들어갔는지로 판단한다.
    // 실제 통과 처리는 QueueAdmissionScheduler가 admit-count(공연 오픈 스케줄러가 설정)를 기준으로 수행한다.
    public boolean isAdmitted(Long scheduleId, Long userId) {
        return waitingQueueRepository.isAdmitted(scheduleId, userId);
    }

    // admit-count(누적 입장 허용 목표)에 아직 못 미친 만큼 대기열 앞에서 사용자를 통과시킨다. 통과시킨 수를 반환.
    // 7주차 ramp-up이 admit-count를 키우면 다음 주기에 자동으로 다음 배치가 통과된다.
    public int admitDueUsers(Long scheduleId) {
        long target = waitingQueueRepository.admitCount(scheduleId);
        if (target <= 0) {
            return 0;
        }
        long slots = target - waitingQueueRepository.admittedSize(scheduleId);
        if (slots <= 0) {
            return 0;
        }
        return waitingQueueRepository.admitFront(scheduleId, slots);
    }

    private void validateSchedule(Long scheduleId) {
        if (!performanceScheduleRepository.existsById(scheduleId)) {
            throw new BusinessException(QueueErrorCode.SCHEDULE_NOT_FOUND);
        }
    }
}
