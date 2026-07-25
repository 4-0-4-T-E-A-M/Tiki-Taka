package io.github.team404.tikitaka.performanceseat.dto;

import io.github.team404.tikitaka.performanceseat.entity.Performance;
import io.github.team404.tikitaka.performanceseat.entity.PerformanceGenre;
import io.github.team404.tikitaka.performanceseat.entity.PerformanceRegion;
import java.time.LocalDateTime;

public record PerformanceResponse(
        Long id,
        String title,
        String artist,
        String venueName,
        PerformanceRegion region,
        PerformanceGenre genre,
        String description,
        String posterUrl,
        LocalDateTime createdAt
) {
    public static PerformanceResponse from(Performance performance) {
        return new PerformanceResponse(
                performance.getId(),
                performance.getTitle(),
                performance.getArtist(),
                performance.getVenueName(),
                performance.getRegion(),
                performance.getGenre(),
                performance.getDescription(),
                performance.getPosterUrl(),
                performance.getCreatedAt()
        );
    }
}
