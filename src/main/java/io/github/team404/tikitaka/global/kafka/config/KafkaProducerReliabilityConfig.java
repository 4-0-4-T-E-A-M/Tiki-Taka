package io.github.team404.tikitaka.global.kafka.config;

import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.boot.autoconfigure.kafka.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Producer 신뢰성 설정(#115). application.yaml이 아니라 코드로 두는 이유: 테스트 리소스의 application.yaml이
// main 설정을 통째로 가려서 yaml 값은 테스트에서 적용·검증되지 않는다. Customizer는 Boot가 만드는
// ProducerFactory 하나(예매 이벤트 + 공연 변경 이벤트 공용)에 항상 적용된다.
//
// kafka-clients 3.x는 기본값이 이미 idempotence=true / acks=all이지만, "명시하지 않은" 멱등성은 충돌하는 설정
// (acks=1, max.in.flight>5 등)이 들어오면 경고 없이 꺼진다. 명시하면 같은 충돌이 기동 시 ConfigException으로 드러난다.
@Configuration
public class KafkaProducerReliabilityConfig {

    // 재시도 시 브로커가 (producerId, sequence)로 중복 기록을 걸러낸다 — "같은 Producer 세션 안의 재전송" 중복만 막는다.
    // 앱이 send()를 두 번 부르는 중복은 막지 못하므로, 그건 eventId 기반 Consumer 멱등 처리(ProcessedEvent) 몫이다.
    static final boolean ENABLE_IDEMPOTENCE = true;

    // 멱등성의 전제 조건. 리더 + ISR 모두 기록해야 성공으로 보므로 리더 장애 시 유실이 없다.
    // 지연은 늘지만 send()가 afterCommit에서 비동기로 나가 예매 API 응답 시간에는 직접 더해지지 않는다.
    static final String ACKS = "all";

    // 멱등성이 켜져 있으면 5 이하에서 재시도 중에도 파티션 내 순서가 보장된다(reservationId 키 순서 유지).
    static final int MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION = 5;

    // 재시도 횟수 대신 delivery.timeout.ms로 "얼마나 오래 재시도할지"를 제한한다(Kafka 권장 방식).
    static final int RETRIES = Integer.MAX_VALUE;

    // 발행 최종 실패까지의 상한. 기본 120초 대신 30초: 연습 세션 리포트·통계가 수 분씩 밀리기보다 빨리 실패로 확정하고
    // 로그로 남기는 편이 낫다. 제약: delivery.timeout.ms >= linger.ms + request.timeout.ms
    static final int DELIVERY_TIMEOUT_MS = 30_000;
    static final int REQUEST_TIMEOUT_MS = 10_000;

    // send() 호출 스레드가 메타데이터 조회·버퍼 대기로 막히는 상한. 기본 60초면 브로커 장애 시 예매 요청 스레드가
    // afterCommit에서 최대 60초 멈추고, 그동안 afterCompletion의 좌석 락 해제도 밀린다. 좌석 락 lease(3초)보다
    // 짧게 둬서 브로커가 죽어도 예매 응답 지연이 2초를 넘지 않게 한다.
    static final int MAX_BLOCK_MS = 2_000;

    @Bean
    public DefaultKafkaProducerFactoryCustomizer producerReliabilityCustomizer() {
        return producerFactory -> producerFactory.updateConfigs(Map.of(
                ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, ENABLE_IDEMPOTENCE,
                ProducerConfig.ACKS_CONFIG, ACKS,
                ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION,
                ProducerConfig.RETRIES_CONFIG, RETRIES,
                ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, DELIVERY_TIMEOUT_MS,
                ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, REQUEST_TIMEOUT_MS,
                ProducerConfig.MAX_BLOCK_MS_CONFIG, MAX_BLOCK_MS
        ));
    }
}
