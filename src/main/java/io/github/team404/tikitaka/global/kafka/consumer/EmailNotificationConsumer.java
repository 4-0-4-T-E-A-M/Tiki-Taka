package io.github.team404.tikitaka.global.kafka.consumer;

import io.github.team404.tikitaka.booking.entity.ReservationStatus;
import io.github.team404.tikitaka.global.exception.BusinessException;
import io.github.team404.tikitaka.global.kafka.event.ReservationEvent;
import io.github.team404.tikitaka.global.kafka.topic.KafkaTopics;
import io.github.team404.tikitaka.notification.service.EmailSender;
import io.github.team404.tikitaka.performanceseat.entity.Performance;
import io.github.team404.tikitaka.performanceseat.entity.PerformanceSchedule;
import io.github.team404.tikitaka.performanceseat.repository.PerformanceRepository;
import io.github.team404.tikitaka.performanceseat.repository.PerformanceScheduleRepository;
import io.github.team404.tikitaka.user.domain.User;
import io.github.team404.tikitaka.user.exception.UserErrorCode;
import io.github.team404.tikitaka.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailNotificationConsumer {

    private final UserRepository userRepository;
    private final PerformanceScheduleRepository performanceScheduleRepository;
    private final PerformanceRepository performanceRepository;
    private final EmailSender emailSender;

    @KafkaListener(
            topics = KafkaTopics.RESERVATION_EVENTS,
            groupId = "email-notification-consumer"
    )
    public void consume(ReservationEvent event) {
        if (event.status() != ReservationStatus.PENDING_PAYMENT) {
            return;
        }

        User user;
        try {
            user = userRepository.findById(event.userId())
                    .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
        } catch (BusinessException e) {
            log.error(
                    "예약 완료 이메일 발송을 위한 사용자 조회 실패. reservationId={}, userId={}",
                    event.reservationId(),
                    event.userId(),
                    e
            );
            throw e;
        }

        PerformanceSchedule schedule;
        try {
            schedule = performanceScheduleRepository.findById(event.scheduleId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "공연 회차를 찾을 수 없습니다: " + event.scheduleId()));
        } catch (ResponseStatusException e) {
            log.error(
                    "예약 완료 이메일 발송을 위한 공연 회차 조회 실패. reservationId={}, scheduleId={}",
                    event.reservationId(),
                    event.scheduleId(),
                    e
            );
            throw e;
        }

        Performance performance;
        try {
            performance = performanceRepository.findById(schedule.getPerformanceId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "공연을 찾을 수 없습니다: " + schedule.getPerformanceId()));
        } catch (ResponseStatusException e) {
            log.error(
                    "예약 완료 이메일 발송을 위한 공연 조회 실패. reservationId={}, scheduleId={}, performanceId={}",
                    event.reservationId(),
                    event.scheduleId(),
                    schedule.getPerformanceId(),
                    e
            );
            throw e;
        }

        try {
            emailSender.sendReservationCompleteEmail(user.getEmail(), user.getName(), performance.getTitle());
        } catch (MailException e) {
            log.error(
                    "예약 완료 이메일 발송 실패. reservationId={}, userId={}",
                    event.reservationId(),
                    event.userId(),
                    e
            );
            throw e;
        }

        log.info(
                "예약 완료 이메일 발송 성공. reservationId={}, userId={}",
                event.reservationId(),
                event.userId()
        );
    }
}
