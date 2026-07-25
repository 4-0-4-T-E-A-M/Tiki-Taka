package io.github.team404.tikitaka.booking.repository;

import io.github.team404.tikitaka.booking.entity.ReservationSeat;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationSeatRepository extends JpaRepository<ReservationSeat, Long> {

    List<ReservationSeat> findAllByReservationId(Long reservationId);
}