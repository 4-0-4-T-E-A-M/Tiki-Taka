package io.github.team404.tikitaka.booking.repository;

import io.github.team404.tikitaka.booking.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
}