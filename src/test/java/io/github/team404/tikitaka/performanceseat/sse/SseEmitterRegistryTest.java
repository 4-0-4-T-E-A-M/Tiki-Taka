package io.github.team404.tikitaka.performanceseat.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SseEmitterRegistryTest {

    private final SseEmitterRegistry registry = new SseEmitterRegistry();

    @Test
    void 등록하면_스냅샷에서_조회된다() {
        // given
        QueueConnectionKey key = new QueueConnectionKey(1L, 10L);
        SseEmitter emitter = new SseEmitter();

        // when
        registry.register(key, emitter);

        // then
        assertThat(registry.snapshot()).containsEntry(key, emitter);
    }

    @Test
    void 커넥션이_완료되면_레지스트리에서_제거된다() {
        // given
        QueueConnectionKey key = new QueueConnectionKey(1L, 10L);
        SseEmitter emitter = mock(SseEmitter.class);
        registry.register(key, emitter);
        registry.updateLastSentRank(key, 3L);

        ArgumentCaptor<Runnable> callback = ArgumentCaptor.forClass(Runnable.class);
        verify(emitter).onCompletion(callback.capture());

        // when — 프레임워크가 실제 async 디스패치 중 완료 콜백을 호출하는 상황을 재현
        callback.getValue().run();

        // then
        assertThat(registry.snapshot()).doesNotContainKey(key);
        assertThat(registry.lastSentRank(key)).isNull();
    }

    @Test
    void 커넥션이_타임아웃되면_레지스트리에서_제거된다() {
        // given
        QueueConnectionKey key = new QueueConnectionKey(1L, 10L);
        SseEmitter emitter = mock(SseEmitter.class);
        registry.register(key, emitter);

        ArgumentCaptor<Runnable> callback = ArgumentCaptor.forClass(Runnable.class);
        verify(emitter).onTimeout(callback.capture());

        // when
        callback.getValue().run();

        // then
        assertThat(registry.snapshot()).doesNotContainKey(key);
    }

    @Test
    void 커넥션이_에러로_종료되면_레지스트리에서_제거된다() {
        // given
        QueueConnectionKey key = new QueueConnectionKey(1L, 10L);
        SseEmitter emitter = mock(SseEmitter.class);
        registry.register(key, emitter);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<Throwable>> callback = ArgumentCaptor.forClass(Consumer.class);
        verify(emitter).onError(callback.capture());

        // when
        callback.getValue().accept(new RuntimeException("연결 끊김"));

        // then
        assertThat(registry.snapshot()).doesNotContainKey(key);
    }
}
