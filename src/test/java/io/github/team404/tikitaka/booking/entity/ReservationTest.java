package io.github.team404.tikitaka.booking.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ReservationTest {

    @Test
    void 생성_직후_상태는_PENDING_PAYMENT이다() {
        // when
        Reservation reservation = reservationOf();

        // then
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
        assertThat(reservation.getCreatedAt()).isNotNull();
    }

    @Test
    void confirm_호출시_CONFIRMED로_전이하고_confirmedAt이_기록된다() {
        // given
        Reservation reservation = reservationOf();

        // when
        reservation.confirm();

        // then
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservation.getConfirmedAt()).isNotNull();
    }

    @Test
    void CONFIRMED_상태의_예매도_취소하면_CANCELED로_전이한다() {
        // given
        Reservation reservation = reservationOf();
        reservation.confirm();

        // when
        reservation.cancel();

        // then
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELED);
        assertThat(reservation.getCanceledAt()).isNotNull();
    }

    @Test
    void 이미_취소된_예매는_다시_확정할_수_없다() {
        // given
        Reservation reservation = reservationOf();
        reservation.cancel();

        // when & then
        assertThatThrownBy(reservation::confirm)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void EXPIRED_상태의_예매는_취소할_수_없다() {
        // given
        Reservation reservation = reservationOf();
        reservation.expire();

        // when & then
        assertThatThrownBy(reservation::cancel)
                .isInstanceOf(IllegalStateException.class);
    }

    private Reservation reservationOf() {
        return Reservation.builder()
                .userId(1L)
                .simulationId(1L)
                .scheduleId(1L)
                .selectedQuantity(2)
                .build();
    }
}