package io.github.team404.tikitaka.performanceseat.search;

import static org.mockito.Mockito.verify;

import io.github.team404.tikitaka.global.kafka.event.PerformanceChangedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PerformanceChangedEventConsumerTest {

    @Mock
    private PerformanceSearchIndexer indexer;

    @InjectMocks
    private PerformanceChangedEventConsumer consumer;

    @Test
    void UPSERT_이벤트는_indexById를_호출한다() {
        consumer.consume(PerformanceChangedEvent.upsert(7L));

        verify(indexer).indexById(7L);
    }

    @Test
    void DELETE_이벤트는_deleteById를_호출한다() {
        consumer.consume(PerformanceChangedEvent.delete(7L));

        verify(indexer).deleteById(7L);
    }
}
