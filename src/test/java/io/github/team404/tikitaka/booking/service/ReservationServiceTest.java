package io.github.team404.tikitaka.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.team404.tikitaka.booking.dto.ReservationCreateRequest;
import io.github.team404.tikitaka.booking.entity.Reservation;
import io.github.team404.tikitaka.booking.entity.ReservationSeat;
import io.github.team404.tikitaka.booking.repository.ReservationRepository;
import io.github.team404.tikitaka.booking.repository.ReservationSeatRepository;
import io.github.team404.tikitaka.performanceseat.entity.Seat;
import io.github.team404.tikitaka.performanceseat.entity.SeatGrade;
import io.github.team404.tikitaka.performanceseat.entity.SeatStatus;
import io.github.team404.tikitaka.performanceseat.repository.SeatRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private ReservationSeatRepository reservationSeatRepository;

    @Mock
    private SeatRepository seatRepository;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock rLock;

    @InjectMocks
    private ReservationService reservationService;

    @Test
    void 예매_생성시_선택한_좌석을_모두_HELD로_전이한다() throws InterruptedException {
        // given
        Seat seat1 = seatOf(1L);
        Seat seat2 = seatOf(2L);
        ReservationCreateRequest request = new ReservationCreateRequest(1L, 1L, 1L, List.of(1L, 2L));
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(seatRepository.findAllById(request.seatIds())).thenReturn(List.of(seat1, seat2));

        // when
        Reservation reservation = reservationService.createReservation(request);

        // then
        assertThat(seat1.getStatus()).isEqualTo(SeatStatus.HELD);
        assertThat(seat2.getStatus()).isEqualTo(SeatStatus.HELD);
        assertThat(reservation.getSelectedQuantity()).isEqualTo(2);
        verify(reservationRepository, times(1)).save(any(Reservation.class));
        verify(reservationSeatRepository, times(2)).save(any(ReservationSeat.class));
    }

    @Test
    void 존재하지_않는_좌석이_포함되면_예외가_발생한다() throws InterruptedException {
        // given
        ReservationCreateRequest request = new ReservationCreateRequest(1L, 1L, 1L, List.of(1L, 2L));
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(seatRepository.findAllById(request.seatIds())).thenReturn(List.of(seatOf(1L)));

        // when & then
        assertThatThrownBy(() -> reservationService.createReservation(request))
                .isInstanceOf(ResponseStatusException.class);
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void 좌석을_선택하지_않으면_예외가_발생한다() {
        // given
        ReservationCreateRequest request = new ReservationCreateRequest(1L, 1L, 1L, List.of());

        // when & then
        assertThatThrownBy(() -> reservationService.createReservation(request))
                .isInstanceOf(ResponseStatusException.class);
        verify(seatRepository, never()).findAllById(anyList());
    }

    @Test
    void 락_획득_후에도_좌석_상태가_AVAILABLE이_아니면_충돌_예외로_즉시_실패한다() throws InterruptedException {
        // given: 락은 정상적으로 획득했지만(정상 경로에선 발생하지 않아야 함) DB 상 좌석이 이미 HELD인 경우
        Seat seat = seatOf(1L);
        seat.hold();
        ReservationCreateRequest request = new ReservationCreateRequest(1L, 1L, 1L, List.of(1L));
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(seatRepository.findAllById(request.seatIds())).thenReturn(List.of(seat));

        // when & then: IllegalStateException이 그대로 새지 않고 409 CONFLICT로 변환되어야 한다
        assertThatThrownBy(() -> reservationService.createReservation(request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void 취소하면_예매와_연결된_좌석이_모두_해제된다() {
        // given
        Reservation reservation = reservationOf();
        Seat seat = seatOf(1L);
        seat.hold();
        ReservationSeat reservationSeat = ReservationSeat.builder()
                .reservationId(10L)
                .seatId(1L)
                .holdExpiresAt(LocalDateTime.now().plusMinutes(5))
                .build();
        when(reservationRepository.findById(10L)).thenReturn(Optional.of(reservation));
        when(reservationSeatRepository.findAllByReservationId(10L)).thenReturn(List.of(reservationSeat));
        when(seatRepository.findById(1L)).thenReturn(Optional.of(seat));

        // when
        reservationService.cancelReservation(10L);

        // then
        assertThat(reservation.getStatus().name()).isEqualTo("CANCELED");
        assertThat(reservationSeat.getReleasedAt()).isNotNull();
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
    }

    private Seat seatOf(Long id) {
        return Seat.builder()
                .sectionId(1L)
                .rowName("A")
                .seatNumber(id.intValue())
                .grade(SeatGrade.VIP)
                .price(100_000)
                .build();
    }

    private Reservation reservationOf() {
        return Reservation.builder()
                .userId(1L)
                .simulationId(1L)
                .scheduleId(1L)
                .selectedQuantity(1)
                .build();
    }
}