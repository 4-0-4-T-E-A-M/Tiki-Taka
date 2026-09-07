package io.github.team404.tikitaka.performanceseat.sse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.team404.tikitaka.global.exception.BusinessException;
import io.github.team404.tikitaka.performanceseat.exception.QueueErrorCode;
import io.github.team404.tikitaka.performanceseat.service.WaitingQueueService;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@ExtendWith(MockitoExtension.class)
class QueueRankPusherTest {

    @Mock
    private WaitingQueueService waitingQueueService;

    @Mock
    private SseEmitterRegistry registry;

    @Mock
    private SseEmitter emitter;

    private final QueueConnectionKey key = new QueueConnectionKey(1L, 10L);

    @Test
    void force가_아니고_순번과_입장여부가_그대로면_전송하지_않는다() throws IOException {
        // given
        when(waitingQueueService.getRank(1L, 10L)).thenReturn(3L);
        when(waitingQueueService.isAdmitted(1L, 10L)).thenReturn(false);
        when(registry.lastSentRank(key)).thenReturn(3L);
        when(registry.lastSentAdmitted(key)).thenReturn(false);

        // when
        newPusher().push(key, emitter, false);

        // then
        verify(emitter, never()).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void 순번이_바뀌면_전송하고_마지막_전송값을_갱신한다() throws IOException {
        // given
        when(waitingQueueService.getRank(1L, 10L)).thenReturn(2L);
        when(waitingQueueService.isAdmitted(1L, 10L)).thenReturn(false);
        when(registry.lastSentRank(key)).thenReturn(3L);
        when(waitingQueueService.size(1L)).thenReturn(5L);

        // when
        newPusher().push(key, emitter, false);

        // then
        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
        verify(registry).updateLastSent(key, 2L, false);
    }

    @Test
    void 순번은_그대로여도_입장_허용으로_바뀌면_전송한다() throws IOException {
        // given — 순번 0에서 대기 중이던 사용자가 통과(admitted)됨
        when(waitingQueueService.getRank(1L, 10L)).thenReturn(0L);
        when(waitingQueueService.isAdmitted(1L, 10L)).thenReturn(true);
        when(registry.lastSentRank(key)).thenReturn(0L);
        when(registry.lastSentAdmitted(key)).thenReturn(false);
        when(waitingQueueService.size(1L)).thenReturn(5L);

        // when
        newPusher().push(key, emitter, false);

        // then
        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
        verify(registry).updateLastSent(key, 0L, true);
    }

    @Test
    void force면_순번이_그대로여도_전송한다() throws IOException {
        // given
        when(waitingQueueService.getRank(1L, 10L)).thenReturn(3L);
        when(waitingQueueService.isAdmitted(1L, 10L)).thenReturn(true);
        when(waitingQueueService.size(1L)).thenReturn(5L);

        // when
        newPusher().push(key, emitter, true);

        // then
        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
        verify(registry).updateLastSent(key, 3L, true);
    }

    @Test
    void 대기열_조회_중_예외가_발생하면_에러로_종료한다() {
        // given
        when(waitingQueueService.getRank(1L, 10L))
                .thenThrow(new BusinessException(QueueErrorCode.QUEUE_ENTRY_NOT_FOUND));

        // when
        newPusher().push(key, emitter, true);

        // then
        verify(emitter).completeWithError(any(BusinessException.class));
    }

    private QueueRankPusher newPusher() {
        return new QueueRankPusher(waitingQueueService, registry);
    }
}
