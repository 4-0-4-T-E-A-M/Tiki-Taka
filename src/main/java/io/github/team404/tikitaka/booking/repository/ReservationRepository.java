package io.github.team404.tikitaka.booking.repository;

import io.github.team404.tikitaka.booking.entity.Reservation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<Reservation, Long>, ReservationRepositoryCustom {

    List<Reservation> findAllByUserId(Long userId);

    // 공연 삭제 시 예매 존재 여부 체크용 (performanceseat 도메인의 공연 삭제 API에서 사용, issue #15)
    boolean existsByScheduleIdIn(List<Long> scheduleIds);
}