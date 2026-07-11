package io.github.team404.tikitaka.performanceseat.dto;

import io.github.team404.tikitaka.performanceseat.entity.SeatGrade;

public record SeatRowRequest(
        String rowName,
        Integer seatCount,
        SeatGrade grade,
        Integer price
) {
}
