package io.github.team404.tikitaka.global.kafka.consumer;

import io.github.team404.tikitaka.booking.entity.ReservationStatus;
import io.github.team404.tikitaka.global.exception.BusinessException;
import io.github.team404.tikitaka.global.kafka.event.ReservationEvent;
import io.github.team404.tikitaka.notification.service.EmailSender;
import io.github.team404.tikitaka.performanceseat.entity.Performance;
import io.github.team404.tikitaka.performanceseat.entity.PerformanceSchedule;
import io.github.team404.tikitaka.performanceseat.repository.PerformanceRepository;
import io.github.team404.tikitaka.performanceseat.repository.PerformanceScheduleRepository;
import io.github.team404.tikitaka.user.domain.OAuthProvider;
import io.github.team404.tikitaka.user.domain.User;
import io.github.team404.tikitaka.user.exception.UserErrorCode;
import io.github.team404.tikitaka.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.mail.MailSendException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class EmailNotificationConsumerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PerformanceScheduleRepository performanceScheduleRepository;

    @Mock
    private PerformanceRepository performanceRepository;

    @Mock
    private EmailSender emailSender;

    @InjectMocks
    private EmailNotificationConsumer consumer;

    @Test
    void PENDING_PAYMENT_이벤트는_사용자를_조회하고_이메일을_발송한다() {
        ReservationEvent event = pendingPaymentEvent();
        User user = new User("user@example.com", "사용자", OAuthProvider.GOOGLE, "provider-id");
        given(userRepository.findById(event.userId())).willReturn(Optional.of(user));
        givenScheduleAndPerformance(event, "뮤지컬 위키드");

        consumer.consume(event);

        verify(userRepository).findById(event.userId());
        verify(performanceScheduleRepository).findById(event.scheduleId());
        verify(performanceRepository).findById(200L);
        verify(emailSender).sendReservationCompleteEmail("user@example.com", "사용자", "뮤지컬 위키드");
    }

    @Test
    void PENDING_PAYMENT가_아닌_이벤트는_사용자_조회와_이메일_발송을_하지_않는다() {
        ReservationEvent event = new ReservationEvent(
                UUID.randomUUID(),
                1L,
                10L,
                100L,
                ReservationStatus.CONFIRMED,
                LocalDateTime.now()
        );

        consumer.consume(event);

        verifyNoInteractions(userRepository, performanceScheduleRepository, performanceRepository, emailSender);
    }

    @Test
    void 사용자가_없으면_BusinessException이_발생한다() {
        ReservationEvent event = pendingPaymentEvent();
        given(userRepository.findById(event.userId())).willReturn(Optional.empty());

        assertThatThrownBy(() -> consumer.consume(event))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND));

        verifyNoInteractions(performanceScheduleRepository, performanceRepository, emailSender);
    }

    @Test
    void 공연_회차가_없으면_예외가_발생하고_이메일을_발송하지_않는다() {
        ReservationEvent event = pendingPaymentEvent();
        User user = new User("user@example.com", "사용자", OAuthProvider.GOOGLE, "provider-id");
        given(userRepository.findById(event.userId())).willReturn(Optional.of(user));
        given(performanceScheduleRepository.findById(event.scheduleId())).willReturn(Optional.empty());

        assertThatThrownBy(() -> consumer.consume(event))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

        verifyNoInteractions(performanceRepository, emailSender);
    }

    @Test
    void 공연이_없으면_예외가_발생하고_이메일을_발송하지_않는다() {
        ReservationEvent event = pendingPaymentEvent();
        User user = new User("user@example.com", "사용자", OAuthProvider.GOOGLE, "provider-id");
        given(userRepository.findById(event.userId())).willReturn(Optional.of(user));
        PerformanceSchedule schedule = mock(PerformanceSchedule.class);
        given(schedule.getPerformanceId()).willReturn(200L);
        given(performanceScheduleRepository.findById(event.scheduleId())).willReturn(Optional.of(schedule));
        given(performanceRepository.findById(200L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> consumer.consume(event))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

        verifyNoInteractions(emailSender);
    }

    @Test
    void 이메일_발송_실패_예외는_Consumer_밖으로_전파된다() {
        ReservationEvent event = pendingPaymentEvent();
        User user = new User("user@example.com", "사용자", OAuthProvider.GOOGLE, "provider-id");
        given(userRepository.findById(event.userId())).willReturn(Optional.of(user));
        givenScheduleAndPerformance(event, "뮤지컬 위키드");
        willThrow(new MailSendException("mail send failed"))
                .given(emailSender).sendReservationCompleteEmail("user@example.com", "사용자", "뮤지컬 위키드");

        assertThatThrownBy(() -> consumer.consume(event))
                .isInstanceOf(MailSendException.class);
    }

    private ReservationEvent pendingPaymentEvent() {
        return new ReservationEvent(
                UUID.randomUUID(),
                1L,
                10L,
                100L,
                ReservationStatus.PENDING_PAYMENT,
                LocalDateTime.now()
        );
    }

    private void givenScheduleAndPerformance(ReservationEvent event, String performanceTitle) {
        PerformanceSchedule schedule = mock(PerformanceSchedule.class);
        Performance performance = mock(Performance.class);
        given(schedule.getPerformanceId()).willReturn(200L);
        given(performance.getTitle()).willReturn(performanceTitle);
        given(performanceScheduleRepository.findById(event.scheduleId())).willReturn(Optional.of(schedule));
        given(performanceRepository.findById(200L)).willReturn(Optional.of(performance));
    }
}
