package io.github.team404.tikitaka.performanceseat.repository;

import io.github.team404.tikitaka.performanceseat.entity.Seat;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeatRepository extends JpaRepository<Seat, Long> {
}