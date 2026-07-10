package io.github.team404.tikitaka.booking.dto;

import io.github.team404.tikitaka.booking.entity.Reservation;
import io.github.team404.tikitaka.booking.entity.ReservationStatus;
import java.time.LocalDateTime;

public record ReservationResponse(
        Long id,
        Long userId,
        Long simulationId,
        Long scheduleId,
        Integer selectedQuantity,
        ReservationStatus status,
        LocalDateTime createdAt,
        LocalDateTime confirmedAt,
        LocalDateTime expiredAt,
        LocalDateTime canceledAt
) {
    public static ReservationResponse from(Reservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getUserId(),
                reservation.getSimulationId(),
                reservation.getScheduleId(),
                reservation.getSelectedQuantity(),
                reservation.getStatus(),
                reservation.getCreatedAt(),
                reservation.getConfirmedAt(),
                reservation.getExpiredAt(),
                reservation.getCanceledAt()
        );
    }
}