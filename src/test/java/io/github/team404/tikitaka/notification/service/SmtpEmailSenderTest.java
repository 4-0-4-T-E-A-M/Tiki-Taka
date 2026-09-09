package io.github.team404.tikitaka.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class SmtpEmailSenderTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private SmtpEmailSender smtpEmailSender;

    @Test
    void 예약_완료_이메일에_사용자명과_공연명을_포함한다() {
        smtpEmailSender.sendReservationCompleteEmail("user@example.com", "조준형", "뮤지컬 위키드");

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());

        SimpleMailMessage message = messageCaptor.getValue();
        assertThat(message.getTo()).containsExactly("user@example.com");
        assertThat(message.getSubject()).isEqualTo("[Tiki-Taka] 예약 완료 안내");
        assertThat(message.getText()).isEqualTo("조준형님, 뮤지컬 위키드 예약이 정상적으로 완료되었습니다.");
    }
}
