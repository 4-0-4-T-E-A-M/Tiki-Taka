package io.github.team404.tikitaka.performanceseat.sse;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

// 대기열 SSE 커넥션을 인메모리로 보관한다. 커넥션마다 폴링 스레드를 두지 않고
// QueueRankBroadcaster 하나가 이 레지스트리 전체를 순회하는 방식을 택했다 —
// 동시 대기자가 수천 명이어도 스레드/스케줄 리소스가 늘어나지 않는다(이슈 #54).
@Component
public class SseEmitterRegistry {

    private final Map<QueueConnectionKey, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final Map<QueueConnectionKey, Long> lastSentRanks = new ConcurrentHashMap<>();

    public void register(QueueConnectionKey key, SseEmitter emitter) {
        emitters.put(key, emitter);
        emitter.onCompletion(() -> remove(key));
        emitter.onTimeout(() -> remove(key));
        emitter.onError(e -> remove(key));
    }

    public Map<QueueConnectionKey, SseEmitter> snapshot() {
        return Map.copyOf(emitters);
    }

    public Long lastSentRank(QueueConnectionKey key) {
        return lastSentRanks.get(key);
    }

    public void updateLastSentRank(QueueConnectionKey key, long rank) {
        lastSentRanks.put(key, rank);
    }

    private void remove(QueueConnectionKey key) {
        emitters.remove(key);
        lastSentRanks.remove(key);
    }
}
