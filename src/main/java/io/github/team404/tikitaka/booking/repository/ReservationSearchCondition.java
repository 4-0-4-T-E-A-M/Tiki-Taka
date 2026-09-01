package io.github.team404.tikitaka.booking.repository;

import io.github.team404.tikitaka.booking.entity.ReservationStatus;
import java.time.LocalDateTime;

// 예매 복합 조건 조회용 검색 조건 - 모든 필드는 선택(null 허용)이며 null인 조건은 무시된다
public record ReservationSearchCondition(
        Long userId,
        Long simulationId,
        Long scheduleId,
        ReservationStatus status,
        LocalDateTime createdFrom,
        LocalDateTime createdTo
) {
}