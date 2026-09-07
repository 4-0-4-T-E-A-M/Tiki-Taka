package io.github.team404.tikitaka.booking.repository;

import io.github.team404.tikitaka.booking.entity.Reservation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ReservationRepositoryCustom {

    // userId/simulationId/scheduleId/status/createdAt 기간을 선택적으로 조합해 조회 (조건이 전부 null이면 전체 조회)
    Page<Reservation> search(ReservationSearchCondition condition, Pageable pageable);
}
