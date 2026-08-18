package io.github.team404.tikitaka.performanceseat.sse;

import io.github.team404.tikitaka.global.exception.BusinessException;
import io.github.team404.tikitaka.performanceseat.dto.QueueStatusResponse;
import io.github.team404.tikitaka.performanceseat.service.WaitingQueueService;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

// 커넥션 최초 연결 시(WaitingQueueController)와 순번 재조회 시(QueueRankBroadcaster)가
// 공유하는 push 로직. rank가 바뀌지 않았으면 전송을 생략해 불필요한 이벤트를 줄인다.
@Component
@RequiredArgsConstructor
public class QueueRankPusher {

    private static final String EVENT_NAME_RANK = "rank";

    private final WaitingQueueService waitingQueueService;
    private final SseEmitterRegistry registry;

    public void push(QueueConnectionKey key, SseEmitter emitter, boolean force) {
        try {
            long rank = waitingQueueService.getRank(key.scheduleId(), key.userId());
            Long lastSentRank = registry.lastSentRank(key);
            if (!force && lastSentRank != null && lastSentRank == rank) {
                return;
            }

            long waitingCount = waitingQueueService.size(key.scheduleId());
            emitter.send(SseEmitter.event().name(EVENT_NAME_RANK).data(QueueStatusResponse.of(rank, waitingCount)));
            registry.updateLastSentRank(key, rank);
        } catch (BusinessException | IOException e) {
            emitter.completeWithError(e);
        }
    }
}
