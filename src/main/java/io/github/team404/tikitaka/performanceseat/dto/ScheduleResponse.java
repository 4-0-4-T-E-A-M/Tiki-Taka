package io.github.team404.tikitaka.performanceseat.dto;

import io.github.team404.tikitaka.performanceseat.entity.PerformanceSchedule;
import io.github.team404.tikitaka.performanceseat.entity.ScheduleStatus;
import java.time.LocalDateTime;

public record ScheduleResponse(
        Long id,
        LocalDateTime performanceDatetime,
        ScheduleStatus status
) {
    public static ScheduleResponse from(PerformanceSchedule schedule) {
        return new ScheduleResponse(
                schedule.getId(),
                schedule.getPerformanceDatetime(),
                schedule.getStatus()
        );
    }
}
