package io.github.team404.tikitaka.performanceseat.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SeatTest {

    @Test
    void 생성_직후_상태는_AVAILABLE이다() {
        // when
        Seat seat = seatOf();

        // then
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
    }

    @Test
    void hold_호출시_HELD로_전이한다() {
        // given
        Seat seat = seatOf();

        // when
        seat.hold();

        // then
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.HELD);
    }

    @Test
    void 이미_HELD인_좌석은_다시_hold할_수_없다() {
        // given
        Seat seat = seatOf();
        seat.hold();

        // when & then
        assertThatThrownBy(seat::hold)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void release_호출시_AVAILABLE로_되돌아온다() {
        // given
        Seat seat = seatOf();
        seat.hold();

        // when
        seat.release();

        // then
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
    }

    @Test
    void AVAILABLE_좌석은_release할_수_없다() {
        // given
        Seat seat = seatOf();

        // when & then
        assertThatThrownBy(seat::release)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void confirm_호출시_RESERVED로_전이한다() {
        // given
        Seat seat = seatOf();
        seat.hold();

        // when
        seat.confirm();

        // then
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.RESERVED);
    }

    @Test
    void AVAILABLE_좌석은_confirm할_수_없다() {
        // given
        Seat seat = seatOf();

        // when & then
        assertThatThrownBy(seat::confirm)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void RESERVED_좌석도_release하면_AVAILABLE로_되돌아온다() {
        // given
        Seat seat = seatOf();
        seat.hold();
        seat.confirm();

        // when
        seat.release();

        // then
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
    }

    private Seat seatOf() {
        return Seat.builder()
                .sectionId(1L)
                .rowName("A")
                .seatNumber(1)
                .grade(SeatGrade.VIP)
                .price(100_000)
                .build();
    }
}