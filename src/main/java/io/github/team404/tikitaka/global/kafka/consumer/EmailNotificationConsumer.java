package io.github.team404.tikitaka.global.kafka.consumer;

import io.github.team404.tikitaka.booking.entity.ReservationStatus;
import io.github.team404.tikitaka.global.exception.BusinessException;
import io.github.team404.tikitaka.global.kafka.event.ReservationEvent;
import io.github.team404.tikitaka.global.kafka.topic.KafkaTopics;
import io.github.team404.tikitaka.user.domain.User;
import io.github.team404.tikitaka.user.exception.UserErrorCode;
import io.github.team404.tikitaka.user.repository.UserRepository;
import io.github.team404.tikitaka.notification.service.EmailSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

        User user = userRepository.findById(event.userId())
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
        String email = user.getEmail();
        emailSender.sendReservationCompleteEmail(email);

        log.info("Kafka reservation event received for email notification. reservationId={}, userId={}",
                event.reservationId(), event.userId());
    }
}
