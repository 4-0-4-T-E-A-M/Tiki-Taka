package io.github.team404.tikitaka.global.kafka.config;

import io.github.team404.tikitaka.global.kafka.event.ReservationEvent;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.mail.MailSendException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.util.backoff.FixedBackOff;

@Slf4j
@Configuration
public class KafkaConsumerErrorHandlingConfig {

    @Bean
    public DefaultErrorHandler kafkaConsumerErrorHandler(
            @Value("${tikitaka.kafka.consumer.retry.interval-ms:1000}") long intervalMs,
            @Value("${tikitaka.kafka.consumer.retry.max-retries:2}") long maxRetries
    ) {
        ConsumerRecordRecoverer recoverer = this::logRecoveredRecord;
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(intervalMs, maxRetries)
        );

        // Runtime exceptions are not retried unless they are known transient failures.
        errorHandler.setClassifications(Map.of(
                MailSendException.class, true,
                DataAccessResourceFailureException.class, true,
                TransientDataAccessException.class, true,
                RecoverableDataAccessException.class, true,
                RedisConnectionFailureException.class, true
        ), false);
        return errorHandler;
    }

    private void logRecoveredRecord(ConsumerRecord<?, ?> record, Exception exception) {
        Object value = record.value();
        Object eventId = value instanceof ReservationEvent event ? event.eventId() : null;
        log.error(
                "Kafka consumer record recovered after failure. topic={}, partition={}, offset={}, key={}, eventId={}",
                record.topic(), record.partition(), record.offset(), record.key(), eventId, exception
        );
    }
}
