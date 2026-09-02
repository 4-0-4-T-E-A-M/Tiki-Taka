package io.github.team404.tikitaka.notification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SmtpEmailSender implements EmailSender {

    private static final String RESERVATION_COMPLETE_SUBJECT = "[Tiki-Taka] 예약 완료 안내";
    private static final String RESERVATION_COMPLETE_TEXT = "예약이 정상적으로 완료되었습니다.";

    private final JavaMailSender mailSender;

    @Override
    public void sendReservationCompleteEmail(String email) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject(RESERVATION_COMPLETE_SUBJECT);
        message.setText(RESERVATION_COMPLETE_TEXT);

        mailSender.send(message);
    }
}
