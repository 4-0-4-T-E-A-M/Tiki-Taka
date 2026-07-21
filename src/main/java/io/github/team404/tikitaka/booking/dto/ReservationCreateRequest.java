package io.github.team404.tikitaka.booking.dto;

import java.util.List;

public record ReservationCreateRequest(
        Long userId,
        Long simulationId,
        Long scheduleId,
        List<Long> seatIds
) {
}