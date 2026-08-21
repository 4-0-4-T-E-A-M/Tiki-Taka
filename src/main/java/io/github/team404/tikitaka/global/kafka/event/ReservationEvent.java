package io.github.team404.tikitaka.global.kafka.event;

import io.github.team404.tikitaka.booking.entity.ReservationStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record ReservationEvent(
        UUID eventId,
        Long reservationId,
        Long userId,
        Long scheduleId,
        ReservationStatus status,
        LocalDateTime occurredAt
) {
}
