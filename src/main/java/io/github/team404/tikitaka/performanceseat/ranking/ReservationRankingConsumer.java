package io.github.team404.tikitaka.performanceseat.ranking;

import io.github.team404.tikitaka.booking.entity.ReservationStatus;
import io.github.team404.tikitaka.global.kafka.event.ReservationEvent;
import io.github.team404.tikitaka.global.kafka.topic.KafkaTopics;
import io.github.team404.tikitaka.performanceseat.entity.PerformanceSchedule;
import io.github.team404.tikitaka.performanceseat.repository.PerformanceScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

// 예매 완료 이벤트 → 인기 공연 랭킹 점수 반영 (#94).
// reservation-events 토픽의 별도 컨슈머 그룹으로, 알림·통계 컨슈머와 독립적으로 소비한다
// (ROADMAP의 "decoupled, multi-consumer").
//
// 주의: 현재 booking 도메인이 ReservationEvent 프로듀서를 아직 배선하지 않았다(KafkaEventProducer는
// 존재하나 호출부 없음). 이 컨슈머는 준비만 해두고, booking이 발행을 시작하면 자동으로 동작한다.
@Component
@RequiredArgsConstructor
public class ReservationRankingConsumer {

    private static final Logger log = LoggerFactory.getLogger(ReservationRankingConsumer.class);

    private final PopularPerformanceRankingService rankingService;
    private final PerformanceScheduleRepository performanceScheduleRepository;

    @KafkaListener(topics = KafkaTopics.RESERVATION_EVENTS, groupId = "popular-performance-ranking")
    public void consume(ReservationEvent event) {
        if (event.status() != ReservationStatus.CONFIRMED) {
            return; // 확정된 예매만 인기 신호로 센다
        }
        performanceScheduleRepository.findById(event.scheduleId())
                .map(PerformanceSchedule::getPerformanceId)
                .ifPresentOrElse(
                        rankingService::recordReservation,
                        () -> log.warn("예매 이벤트의 회차를 찾을 수 없어 랭킹 반영 생략. scheduleId={}", event.scheduleId()));
    }
}
