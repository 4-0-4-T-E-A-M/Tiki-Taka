package io.github.team404.tikitaka.booking.service;

import io.github.team404.tikitaka.booking.dto.ReservationCreateRequest;
import io.github.team404.tikitaka.booking.entity.Reservation;
import io.github.team404.tikitaka.booking.entity.ReservationSeat;
import io.github.team404.tikitaka.booking.repository.ReservationRepository;
import io.github.team404.tikitaka.booking.repository.ReservationSeatRepository;
import io.github.team404.tikitaka.performanceseat.entity.Seat;
import io.github.team404.tikitaka.performanceseat.repository.SeatRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

// 좌석 홀드 + 예매 생성을 한 트랜잭션으로 묶어둔다 (3주차에 이 경계 안으로 분산 락 통합 예정)
@Service
@RequiredArgsConstructor
public class ReservationService {

    private static final Duration HOLD_TTL = Duration.ofMinutes(5);

    private final ReservationRepository reservationRepository;
    private final ReservationSeatRepository reservationSeatRepository;
    private final SeatRepository seatRepository;

    @Transactional
    public Reservation createReservation(ReservationCreateRequest request) {
        List<Long> seatIds = request.seatIds();
        if (seatIds == null || seatIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "좌석을 1개 이상 선택해야 합니다.");
        }

        List<Seat> seats = seatRepository.findAllById(seatIds);
        if (seats.size() != seatIds.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 좌석이 포함되어 있습니다.");
        }
        seats.forEach(Seat::hold);

        Reservation reservation = Reservation.builder()
                .userId(request.userId())
                .simulationId(request.simulationId())
                .scheduleId(request.scheduleId())
                .selectedQuantity(seats.size())
                .build();
        reservationRepository.save(reservation);

        LocalDateTime holdExpiresAt = LocalDateTime.now().plus(HOLD_TTL);
        for (Seat seat : seats) {
            reservationSeatRepository.save(ReservationSeat.builder()
                    .reservationId(reservation.getId())
                    .seatId(seat.getId())
                    .holdExpiresAt(holdExpiresAt)
                    .build());
        }

        return reservation;
    }

    @Transactional(readOnly = true)
    public Reservation getReservation(Long reservationId) {
        return reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "예매를 찾을 수 없습니다: " + reservationId));
    }

    @Transactional(readOnly = true)
    public List<Reservation> getReservationsByUser(Long userId) {
        return reservationRepository.findAllByUserId(userId);
    }

    @Transactional
    public void cancelReservation(Long reservationId) {
        Reservation reservation = getReservation(reservationId);
        reservation.cancel();

        List<ReservationSeat> reservationSeats = reservationSeatRepository.findAllByReservationId(reservationId);
        for (ReservationSeat reservationSeat : reservationSeats) {
            reservationSeat.release();
            seatRepository.findById(reservationSeat.getSeatId()).ifPresent(Seat::release);
        }
    }
}