package io.github.team404.tikitaka.performanceseat.controller;

import io.github.team404.tikitaka.global.security.principal.CustomUserPrincipal;
import io.github.team404.tikitaka.performanceseat.dto.QueueStatusResponse;
import io.github.team404.tikitaka.performanceseat.service.WaitingQueueService;
import io.github.team404.tikitaka.performanceseat.sse.QueueConnectionKey;
import io.github.team404.tikitaka.performanceseat.sse.QueueRankPusher;
import io.github.team404.tikitaka.performanceseat.sse.SseEmitterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/schedules/{scheduleId}/queue")
@RequiredArgsConstructor
public class WaitingQueueController {

    private final WaitingQueueService waitingQueueService;
    private final SseEmitterRegistry sseEmitterRegistry;
    private final QueueRankPusher queueRankPusher;

    @Value("${queue.sse.timeout-ms:300000}")
    private long sseTimeoutMs;

    @PostMapping
    public ResponseEntity<QueueStatusResponse> enter(
            @PathVariable Long scheduleId, @AuthenticationPrincipal CustomUserPrincipal principal) {
        Long userId = principal.getUserId();
        long rank = waitingQueueService.enter(scheduleId, userId);
        long waitingCount = waitingQueueService.size(scheduleId);
        boolean admitted = waitingQueueService.isAdmitted(scheduleId, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(QueueStatusResponse.of(rank, waitingCount, admitted));
    }

    @GetMapping("/me")
    public QueueStatusResponse getMyStatus(
            @PathVariable Long scheduleId, @AuthenticationPrincipal CustomUserPrincipal principal) {
        Long userId = principal.getUserId();
        long rank = waitingQueueService.getRank(scheduleId, userId);
        long waitingCount = waitingQueueService.size(scheduleId);
        boolean admitted = waitingQueueService.isAdmitted(scheduleId, userId);
        return QueueStatusResponse.of(rank, waitingCount, admitted);
    }

    // 순번이 바뀔 때마다(짧은 주기로) 서버가 클라이언트에 push한다. push되는 QueueStatusResponse에는
    // 입장 허용 여부(admitted)도 포함된다 — 오픈 스케줄러(#75)가 admitCount를 설정하면서 의미가 생겼다.
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable Long scheduleId, @AuthenticationPrincipal CustomUserPrincipal principal) {
        QueueConnectionKey key = new QueueConnectionKey(scheduleId, principal.getUserId());
        SseEmitter emitter = new SseEmitter(sseTimeoutMs);
        sseEmitterRegistry.register(key, emitter);
        queueRankPusher.push(key, emitter, true);
        return emitter;
    }
}
