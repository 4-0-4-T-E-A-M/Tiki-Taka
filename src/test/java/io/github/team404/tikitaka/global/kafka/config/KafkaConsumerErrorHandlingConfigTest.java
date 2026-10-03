package io.github.team404.tikitaka.global.kafka.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.github.team404.tikitaka.global.exception.BusinessException;
import io.github.team404.tikitaka.user.exception.UserErrorCode;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.mail.MailSendException;
import org.springframework.kafka.listener.MessageListenerContainer;

class KafkaConsumerErrorHandlingConfigTest {

    private final KafkaConsumerErrorHandlingConfig config = new KafkaConsumerErrorHandlingConfig();
    private final Consumer<String, String> consumer = mock(Consumer.class);
    private final MessageListenerContainer container = mock(MessageListenerContainer.class);
    private final ConsumerRecord<String, String> record = new ConsumerRecord<>("events", 0, 4L, "key", "value");

    @Test
    void transient한_실패는_두번_재시도한_뒤_최종_복구한다() {
        DefaultErrorHandler handler = config.kafkaConsumerErrorHandler(0, 2);
        Exception failure = new TransientDataAccessResourceException("temporary database failure");

        assertThat(handler.handleOne(failure, record, consumer, container)).isFalse();
        assertThat(handler.handleOne(failure, record, consumer, container)).isFalse();
        assertThat(handler.handleOne(failure, record, consumer, container)).isTrue();
    }

    @Test
    void 사용자_미존재_BusinessException은_재시도하지_않고_즉시_복구한다() {
        DefaultErrorHandler handler = config.kafkaConsumerErrorHandler(0, 2);
        Exception failure = new BusinessException(UserErrorCode.USER_NOT_FOUND);

        assertThat(handler.handleOne(failure, record, consumer, container)).isTrue();
    }

    @Test
    void 일시적인_SMTP_발송_실패는_재시도한다() {
        DefaultErrorHandler handler = config.kafkaConsumerErrorHandler(0, 2);
        Exception failure = new MailSendException("temporary SMTP failure");

        assertThat(handler.handleOne(failure, record, consumer, container)).isFalse();
    }

    @Test
    void 역직렬화_실패는_재시도하지_않고_즉시_복구한다() {
        DefaultErrorHandler handler = config.kafkaConsumerErrorHandler(0, 2);
        Exception failure = new DeserializationException(
                "invalid JSON", new byte[]{'{', '}'}, false, new IllegalArgumentException("invalid payload")
        );

        assertThat(handler.handleOne(failure, record, consumer, container)).isTrue();
    }
}
