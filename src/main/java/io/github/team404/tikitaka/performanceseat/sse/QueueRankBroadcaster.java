package io.github.team404.tikitaka.performanceseat.sse;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 등록된 모든 SSE 커넥션을 단일 스케줄 태스크로 순회하며 순번 변경분만 push한다.
// 커넥션마다 폴링 스레드를 두는 대신 이 방식을 택한 이유는 SseEmitterRegistry 참고.
@Component
@RequiredArgsConstructor
public class QueueRankBroadcaster {

    private final SseEmitterRegistry registry;
    private final QueueRankPusher pusher;

    @Scheduled(fixedDelayString = "${queue.sse.poll-interval-ms:1000}")
    public void broadcastRankChanges() {
        registry.snapshot().forEach((key, emitter) -> pusher.push(key, emitter, false));
    }
}
