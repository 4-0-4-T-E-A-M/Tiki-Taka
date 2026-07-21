package io.github.team404.tikitaka.performanceseat.dto;

import io.github.team404.tikitaka.performanceseat.entity.Performance;
import java.util.List;

public record PerformanceDetailResponse(
        PerformanceResponse performance,
        List<ScheduleResponse> schedules
) {
    public static PerformanceDetailResponse of(Performance performance, List<ScheduleResponse> schedules) {
        return new PerformanceDetailResponse(PerformanceResponse.from(performance), schedules);
    }
}
