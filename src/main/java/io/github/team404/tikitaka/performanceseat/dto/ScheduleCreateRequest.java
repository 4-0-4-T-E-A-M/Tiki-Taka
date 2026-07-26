package io.github.team404.tikitaka.performanceseat.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ScheduleCreateRequest(
        LocalDateTime performanceDatetime,
        List<SectionCreateRequest> sections
) {
}
