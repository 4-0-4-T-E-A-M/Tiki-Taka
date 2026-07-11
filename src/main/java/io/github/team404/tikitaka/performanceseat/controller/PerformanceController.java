package io.github.team404.tikitaka.performanceseat.controller;

import io.github.team404.tikitaka.performanceseat.dto.PerformanceCreateRequest;
import io.github.team404.tikitaka.performanceseat.dto.PerformanceDetailResponse;
import io.github.team404.tikitaka.performanceseat.dto.PerformanceResponse;
import io.github.team404.tikitaka.performanceseat.dto.PerformanceUpdateRequest;
import io.github.team404.tikitaka.performanceseat.dto.ScheduleResponse;
import io.github.team404.tikitaka.performanceseat.entity.Performance;
import io.github.team404.tikitaka.performanceseat.service.PerformanceService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/performances")
@RequiredArgsConstructor
public class PerformanceController {

    private final PerformanceService performanceService;

    @PostMapping
    public ResponseEntity<PerformanceResponse> create(@RequestBody PerformanceCreateRequest request) {
        PerformanceResponse response = PerformanceResponse.from(performanceService.createPerformance(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{performanceId}")
    public PerformanceResponse update(
            @PathVariable Long performanceId, @RequestBody PerformanceUpdateRequest request) {
        return PerformanceResponse.from(performanceService.updatePerformance(performanceId, request));
    }

    @DeleteMapping("/{performanceId}")
    public ResponseEntity<Void> delete(@PathVariable Long performanceId) {
        performanceService.deletePerformance(performanceId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public List<PerformanceResponse> list() {
        return performanceService.listPerformances().stream()
                .map(PerformanceResponse::from)
                .toList();
    }

    @GetMapping("/{performanceId}")
    public PerformanceDetailResponse get(@PathVariable Long performanceId) {
        Performance performance = performanceService.getPerformance(performanceId);
        List<ScheduleResponse> schedules = performanceService.getSchedules(performanceId).stream()
                .map(ScheduleResponse::from)
                .toList();
        return PerformanceDetailResponse.of(performance, schedules);
    }
}
