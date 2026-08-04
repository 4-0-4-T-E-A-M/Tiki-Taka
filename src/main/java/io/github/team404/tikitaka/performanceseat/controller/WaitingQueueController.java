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

    // 순번이 바뀔 때마다(짧은 주기로) 서버가 클라이언트에 push한다. 입장 허용(admitted) 이벤트는
    // 공연 오픈 스케줄러가 admitCount를 산정해야 의미가 있어 이번 이슈 범위에서는 제외했다 —
    // 해당 스케줄러 구현 이슈에서 이어서 추가한다.
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable Long scheduleId, @AuthenticationPrincipal CustomUserPrincipal principal) {
        QueueConnectionKey key = new QueueConnectionKey(scheduleId, principal.getUserId());
        SseEmitter emitter = new SseEmitter(sseTimeoutMs);
        sseEmitterRegistry.register(key, emitter);
        queueRankPusher.push(key, emitter, true);
        return emitter;
    }
}
