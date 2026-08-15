package io.github.team404.tikitaka.performanceseat.controller;

import io.github.team404.tikitaka.global.security.principal.CustomUserPrincipal;
import io.github.team404.tikitaka.performanceseat.dto.QueueStatusResponse;
import io.github.team404.tikitaka.performanceseat.service.WaitingQueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/schedules/{scheduleId}/queue")
@RequiredArgsConstructor
public class WaitingQueueController {

    private final WaitingQueueService waitingQueueService;

    @PostMapping
    public ResponseEntity<QueueStatusResponse> enter(
            @PathVariable Long scheduleId, @AuthenticationPrincipal CustomUserPrincipal principal) {
        long rank = waitingQueueService.enter(scheduleId, principal.getUserId());
        long waitingCount = waitingQueueService.size(scheduleId);
        return ResponseEntity.status(HttpStatus.CREATED).body(QueueStatusResponse.of(rank, waitingCount));
    }

    @GetMapping("/me")
    public QueueStatusResponse getMyStatus(
            @PathVariable Long scheduleId, @AuthenticationPrincipal CustomUserPrincipal principal) {
        long rank = waitingQueueService.getRank(scheduleId, principal.getUserId());
        long waitingCount = waitingQueueService.size(scheduleId);
        return QueueStatusResponse.of(rank, waitingCount);
    }
}
