package io.github.team404.tikitaka.global.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.team404.tikitaka.global.kafka.entity.ProcessedEvent;
import io.github.team404.tikitaka.global.kafka.repository.ProcessedEventRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class ProcessedEventRepositoryTest {

    private static final UUID EVENT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final LocalDateTime PROCESSED_AT =
            LocalDateTime.of(2026, 8, 21, 10, 30);

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void 처리된_이벤트를_저장하고_조회할_수_있다() {
        ProcessedEvent processedEvent = createProcessedEvent(EVENT_ID, PROCESSED_AT);

        ProcessedEvent savedEvent = processedEventRepository.saveAndFlush(processedEvent);

        assertThat(savedEvent.getEventId()).isEqualTo(EVENT_ID);
        assertThat(savedEvent.getProcessedAt()).isEqualTo(PROCESSED_AT);

        ProcessedEvent foundEvent = processedEventRepository.findById(EVENT_ID).orElseThrow();
        assertThat(foundEvent.getEventId()).isEqualTo(EVENT_ID);
        assertThat(foundEvent.getProcessedAt()).isEqualTo(PROCESSED_AT);
    }

    @Test
    void eventId로_처리된_이벤트_존재_여부를_조회할_수_있다() {
        processedEventRepository.saveAndFlush(createProcessedEvent(EVENT_ID, PROCESSED_AT));

        assertThat(processedEventRepository.existsById(EVENT_ID)).isTrue();
        assertThat(processedEventRepository.existsById(UUID.randomUUID())).isFalse();
    }

    @Test
    void 동일한_eventId를_중복_저장할_수_없다() {
        ProcessedEvent firstEvent = createProcessedEvent(EVENT_ID, PROCESSED_AT);
        processedEventRepository.saveAndFlush(firstEvent);
        ProcessedEvent duplicateEvent = createProcessedEvent(
                EVENT_ID,
                PROCESSED_AT.plusMinutes(1)
        );
        entityManager.clear();

        assertThatThrownBy(() -> {
            entityManager.persist(duplicateEvent);
            entityManager.flush();
        })
                .isInstanceOf(ConstraintViolationException.class);
    }

    private ProcessedEvent createProcessedEvent(UUID eventId, LocalDateTime processedAt) {
        return ProcessedEvent.builder()
                .eventId(eventId)
                .processedAt(processedAt)
                .build();
    }
}
