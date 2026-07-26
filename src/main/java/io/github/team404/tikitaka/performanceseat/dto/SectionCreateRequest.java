package io.github.team404.tikitaka.performanceseat.dto;

import java.util.List;

public record SectionCreateRequest(
        String name,
        List<SeatRowRequest> rows
) {
}
