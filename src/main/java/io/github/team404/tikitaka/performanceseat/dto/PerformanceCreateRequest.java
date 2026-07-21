package io.github.team404.tikitaka.performanceseat.dto;

import io.github.team404.tikitaka.performanceseat.entity.PerformanceGenre;
import io.github.team404.tikitaka.performanceseat.entity.PerformanceRegion;
import java.util.List;

public record PerformanceCreateRequest(
        String title,
        String artist,
        String venueName,
        PerformanceRegion region,
        PerformanceGenre genre,
        String description,
        String posterUrl,
        List<ScheduleCreateRequest> schedules
) {
}
