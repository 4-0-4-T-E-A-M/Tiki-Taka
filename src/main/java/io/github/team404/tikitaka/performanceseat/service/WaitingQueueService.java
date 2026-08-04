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
    public long enter(Long scheduleId, Long userId) {
        validateSchedule(scheduleId);
        waitingQueueRepository.addIfAbsent(scheduleId, userId);
        return getRank(scheduleId, userId);
    }

    public long getRank(Long scheduleId, Long userId) {
        Long rank = waitingQueueRepository.rank(scheduleId, userId);
        if (rank == null) {
            throw new BusinessException(QueueErrorCode.QUEUE_ENTRY_NOT_FOUND);
        }
        return rank;
    }

    public long size(Long scheduleId) {
        Long size = waitingQueueRepository.size(scheduleId);
        return size == null ? 0 : size;
    }

    // 입장 허용 기준 — admitCount는 호출 측(공연 오픈 스케줄러 등)이 넘겨준다.
    public boolean isAdmitted(Long scheduleId, Long userId, long admitCount) {
        return getRank(scheduleId, userId) < admitCount;
    }

    private void validateSchedule(Long scheduleId) {
        if (!performanceScheduleRepository.existsById(scheduleId)) {
            throw new BusinessException(QueueErrorCode.SCHEDULE_NOT_FOUND);
        }
    }
}
