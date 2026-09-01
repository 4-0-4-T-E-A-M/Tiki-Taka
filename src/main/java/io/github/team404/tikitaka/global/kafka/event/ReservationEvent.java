package io.github.team404.tikitaka.global.kafka.event;

import io.github.team404.tikitaka.booking.entity.ReservationStatus;
import java.time.LocalDateTime;

public record ReservationEvent(
        Long reservationId,
        Long userId,
        Long scheduleId,
        ReservationStatus status,
        LocalDateTime occurredAt
) {
}
