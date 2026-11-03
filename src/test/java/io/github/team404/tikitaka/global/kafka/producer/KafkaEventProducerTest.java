package io.github.team404.tikitaka.global.kafka.producer;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.team404.tikitaka.booking.entity.ReservationStatus;
import io.github.team404.tikitaka.global.kafka.event.ReservationEvent;
import io.github.team404.tikitaka.global.kafka.topic.KafkaTopics;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class KafkaEventProducerTest {

    private static final UUID SUCCESS_EVENT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID FAILURE_EVENT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000102");

    @Mock
    private KafkaTemplate<String, ReservationEvent> kafkaTemplate;

    @Test
    void reservationId를_메시지_키로_이벤트를_발행한다() {
        // given
        KafkaEventProducer producer = new KafkaEventProducer(kafkaTemplate);
        ReservationEvent event = new ReservationEvent(
                SUCCESS_EVENT_ID, 1L, 10L, 100L, ReservationStatus.PENDING_PAYMENT, LocalDateTime.now());
        SendResult<String, ReservationEvent> sendResult = mock(SendResult.class);
        when(sendResult.getRecordMetadata()).thenReturn(mock(RecordMetadata.class));
        when(kafkaTemplate.send(anyString(), anyString(), any(ReservationEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(sendResult));

        // when
        producer.send(event);

        // then
        verify(kafkaTemplate).send(KafkaTopics.RESERVATION_EVENTS, "1", event);
    }

    @Test
    void 발행이_실패해도_예외를_밖으로_던지지_않는다() {
        // given: 브로커 장애 등으로 발행 자체가 실패하는 상황을 흉내낸다
        KafkaEventProducer producer = new KafkaEventProducer(kafkaTemplate);
        ReservationEvent event = new ReservationEvent(
                FAILURE_EVENT_ID, 1L, 10L, 100L, ReservationStatus.PENDING_PAYMENT, LocalDateTime.now());
        when(kafkaTemplate.send(anyString(), anyString(), any(ReservationEvent.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        // when & then: 로그만 남기고 호출자에게는 예외가 전파되지 않아야 한다
        assertThatCode(() -> producer.send(event)).doesNotThrowAnyException();
    }

    @Test
    void send_호출_자체가_예외를_던져도_밖으로_전파하지_않는다() {
        // given: 메타데이터 조회가 max.block.ms를 넘는 등 send()가 동기적으로 실패하는 상황(#115)
        KafkaEventProducer producer = new KafkaEventProducer(kafkaTemplate);
        ReservationEvent event = new ReservationEvent(
                FAILURE_EVENT_ID, 1L, 10L, 100L, ReservationStatus.PENDING_PAYMENT, LocalDateTime.now());
        when(kafkaTemplate.send(anyString(), anyString(), any(ReservationEvent.class)))
                .thenThrow(new KafkaException("Topic not present in metadata after 2000 ms."));

        // when & then
        assertThatCode(() -> producer.send(event)).doesNotThrowAnyException();
    }
}
