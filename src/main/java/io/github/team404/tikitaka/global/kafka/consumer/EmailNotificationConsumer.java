package io.github.team404.tikitaka.global.kafka.consumer;

import io.github.team404.tikitaka.booking.entity.ReservationStatus;
import io.github.team404.tikitaka.global.exception.BusinessException;
import io.github.team404.tikitaka.global.kafka.event.ReservationEvent;
import io.github.team404.tikitaka.global.kafka.topic.KafkaTopics;
import io.github.team404.tikitaka.notification.service.EmailSender;
import io.github.team404.tikitaka.user.domain.User;
import io.github.team404.tikitaka.user.exception.UserErrorCode;
import io.github.team404.tikitaka.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailNotificationConsumer {

    private final UserRepository userRepository;
    private final EmailSender emailSender;

    @KafkaListener(
            topics = KafkaTopics.RESERVATION_EVENTS,
            groupId = "email-notification-consumer"
    )
    public void consume(ReservationEvent event) {
        if (event.status() != ReservationStatus.CONFIRMED) {
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

        String email = user.getEmail();

        try {
            emailSender.sendReservationCompleteEmail(email);
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