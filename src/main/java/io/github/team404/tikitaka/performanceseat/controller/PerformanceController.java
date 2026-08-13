package io.github.team404.tikitaka.performanceseat.controller;

import io.github.team404.tikitaka.global.response.BaseResponse;
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
    public ResponseEntity<BaseResponse<PerformanceResponse>> create(@RequestBody PerformanceCreateRequest request) {
        PerformanceResponse response = PerformanceResponse.from(performanceService.createPerformance(request));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.success("공연 생성에 성공했습니다.", response));
    }

    @PutMapping("/{performanceId}")
    public ResponseEntity<BaseResponse<PerformanceResponse>> update(
            @PathVariable Long performanceId, @RequestBody PerformanceUpdateRequest request) {
        PerformanceResponse response = PerformanceResponse.from(
                performanceService.updatePerformance(performanceId, request));
        return ResponseEntity.ok(BaseResponse.success("공연 수정에 성공했습니다.", response));
    }

    @DeleteMapping("/{performanceId}")
    public ResponseEntity<BaseResponse<Void>> delete(@PathVariable Long performanceId) {
        performanceService.deletePerformance(performanceId);
        return ResponseEntity.ok(BaseResponse.success("공연 삭제에 성공했습니다."));
    }

    @GetMapping
    public ResponseEntity<BaseResponse<List<PerformanceResponse>>> list() {
        List<PerformanceResponse> responses = performanceService.listPerformances().stream()
                .map(PerformanceResponse::from)
                .toList();
        return ResponseEntity.ok(BaseResponse.success("공연 목록 조회에 성공했습니다.", responses));
    }

    @GetMapping("/{performanceId}")
    public ResponseEntity<BaseResponse<PerformanceDetailResponse>> get(@PathVariable Long performanceId) {
        Performance performance = performanceService.getPerformance(performanceId);
        List<ScheduleResponse> schedules = performanceService.getSchedules(performanceId).stream()
                .map(ScheduleResponse::from)
                .toList();
        PerformanceDetailResponse response = PerformanceDetailResponse.of(performance, schedules);
        return ResponseEntity.ok(BaseResponse.success("공연 조회에 성공했습니다.", response));
    }
}
