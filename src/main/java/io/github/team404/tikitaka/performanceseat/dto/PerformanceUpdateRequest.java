package io.github.team404.tikitaka.performanceseat.dto;

import io.github.team404.tikitaka.performanceseat.entity.PerformanceGenre;
import io.github.team404.tikitaka.performanceseat.entity.PerformanceRegion;

public record PerformanceUpdateRequest(
        String title,
        String artist,
        String venueName,
        PerformanceRegion region,
        PerformanceGenre genre,
        String description,
        String posterUrl
) {
}
