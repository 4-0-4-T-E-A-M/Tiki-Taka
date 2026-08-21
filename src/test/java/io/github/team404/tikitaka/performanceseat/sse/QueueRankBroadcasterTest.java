package io.github.team404.tikitaka.performanceseat.sse;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@ExtendWith(MockitoExtension.class)
class QueueRankBroadcasterTest {

    @Mock
    private SseEmitterRegistry registry;

    @Mock
    private QueueRankPusher pusher;

    @InjectMocks
    private QueueRankBroadcaster broadcaster;

    @Test
    void 등록된_모든_커넥션에_대해_force없이_push한다() {
        // given
        QueueConnectionKey key1 = new QueueConnectionKey(1L, 10L);
        QueueConnectionKey key2 = new QueueConnectionKey(1L, 20L);
        SseEmitter emitter1 = new SseEmitter();
        SseEmitter emitter2 = new SseEmitter();
        when(registry.snapshot()).thenReturn(Map.of(key1, emitter1, key2, emitter2));

        // when
        broadcaster.broadcastRankChanges();

        // then
        verify(pusher).push(key1, emitter1, false);
        verify(pusher).push(key2, emitter2, false);
    }
}
