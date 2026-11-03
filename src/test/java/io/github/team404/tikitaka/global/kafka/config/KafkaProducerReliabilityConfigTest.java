package io.github.team404.tikitaka.global.kafka.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

// #115: Producer 신뢰성 설정이 Boot가 만드는 ProducerFactory에 실제로 적용되는지,
// 그리고 브로커 장애 시 send() 호출 스레드가 max.block.ms 이상 막히지 않는지 검증한다.
class KafkaProducerReliabilityConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KafkaAutoConfiguration.class))
            .withUserConfiguration(KafkaProducerReliabilityConfig.class)
            .withPropertyValues(
                    "spring.kafka.producer.key-serializer=" + StringSerializer.class.getName(),
                    "spring.kafka.producer.value-serializer=" + StringSerializer.class.getName());

    @Test
    void 멱등성_acks_재시도_타임아웃_설정이_ProducerFactory에_명시적으로_적용된다() {
        contextRunner.run(context -> {
            Map<String, Object> configs = context.getBean(DefaultKafkaProducerFactory.class)
                    .getConfigurationProperties();

            assertThat(configs)
                    .containsEntry(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true)
                    .containsEntry(ProducerConfig.ACKS_CONFIG, "all")
                    .containsEntry(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5)
                    .containsEntry(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE)
                    .containsEntry(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 30_000)
                    .containsEntry(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 10_000)
                    .containsEntry(ProducerConfig.MAX_BLOCK_MS_CONFIG, 2_000);
        });
    }

    @Test
    void yaml에서_acks를_낮춰도_멱등성_전제인_acks_all이_유지된다() {
        contextRunner
                .withPropertyValues("spring.kafka.producer.acks=1")
                .run(context -> assertThat(context.getBean(DefaultKafkaProducerFactory.class)
                        .getConfigurationProperties())
                        .containsEntry(ProducerConfig.ACKS_CONFIG, "all"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void 브로커에_연결할_수_없으면_send는_max_block_ms_안에_실패한다() {
        contextRunner
                // 아무도 리슨하지 않는 포트 — 메타데이터 조회가 끝나지 않는 브로커 장애 상황
                .withPropertyValues("spring.kafka.bootstrap-servers=localhost:1")
                .run(context -> {
                    KafkaTemplate<String, String> kafkaTemplate = context.getBean(KafkaTemplate.class);

                    long startedAt = System.nanoTime();
                    Throwable failure = catchThrowable(() ->
                            kafkaTemplate.send("reservation-events", "1", "event").get(10, TimeUnit.SECONDS));
                    Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

                    assertThat(failure).isNotNull();
                    // 기본값(60초)이 아니라 2초 근처에서 실패해야 한다. 스케줄링 오차를 감안해 상한은 넉넉히 둔다.
                    assertThat(elapsed).isLessThan(Duration.ofSeconds(5));
                });
    }
}
