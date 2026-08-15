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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

// 좌석 홀드 + 예매 생성을 한 트랜잭션으로 묶고, 좌석 단위 분산 락(seat-lock:{seatId})으로 경합을 막는다
@Service
@RequiredArgsConstructor
public class ReservationService {

    private static final Duration HOLD_TTL = Duration.ofMinutes(5);
    private static final String LOCK_KEY_PREFIX = "seat-lock:";
    // 락 경합 시 대기 없이 즉시 실패(waitTime=0), leaseTime은 임계 구역(좌석 조회+HELD 전이+저장)이
    // 정상적으로는 수십~수백ms 내 끝나는 점을 감안해 지연을 흡수할 여유를 둔 3초로 결정
    private static final long LOCK_WAIT_SECONDS = 0L;
    private static final long LOCK_LEASE_SECONDS = 3L;

    private final ReservationRepository reservationRepository;
    private final ReservationSeatRepository reservationSeatRepository;
    private final SeatRepository seatRepository;
    private final RedissonClient redissonClient;

    @Transactional
    public Reservation createReservation(ReservationCreateRequest request) {
        List<Long> seatIds = request.seatIds();
        if (seatIds == null || seatIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "좌석을 1개 이상 선택해야 합니다.");
        }

        // 데드락 방지를 위해 좌석 ID를 정렬한 순서대로 락을 건다
        List<Long> lockOrderedSeatIds = seatIds.stream().distinct().sorted().toList();
        List<RLock> acquiredLocks = new ArrayList<>();
        for (Long seatId : lockOrderedSeatIds) {
            RLock lock = redissonClient.getLock(LOCK_KEY_PREFIX + seatId);
            boolean acquired;
            try {
                acquired = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                unlockAll(acquiredLocks);
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "좌석 선점 처리 중 인터럽트가 발생했습니다.");
            }
            if (!acquired) {
                unlockAll(acquiredLocks);
                throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 다른 사용자가 선점 중인 좌석입니다: seatId=" + seatId);
            }
            acquiredLocks.add(lock);
        }
        // 트랜잭션 커밋 전에 락이 풀리면 다음 스레드가 아직 커밋 안 된(AVAILABLE로 보이는) 좌석을
        // 잡을 수 있으므로, 언락은 반드시 커밋/롤백이 끝난 뒤(afterCompletion)로 미룬다.
        // 관리되는 트랜잭션이 없는 호출(예: 순수 단위 테스트)에서는 즉시 해제로 폴백한다.
        boolean transactionActive = TransactionSynchronizationManager.isSynchronizationActive();
        if (transactionActive) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    unlockAll(acquiredLocks);
                }
            });
        }

        try {
            List<Seat> seats = seatRepository.findAllById(seatIds);
            if (seats.size() != seatIds.size()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 좌석이 포함되어 있습니다.");
            }
            // 락 획득 후에도 상태를 다시 확인한다 (hold()의 상태 가드가 AVAILABLE이 아니면 예외를 던짐).
            // 정상 경로에서는 락이 단독 접근을 보장하므로 걸리지 않아야 하지만, 걸린다면
            // 500이 아니라 다른 실패와 동일하게 즉시 실패 응답(409)으로 알린다.
            for (Seat seat : seats) {
                try {
                    seat.hold();
                } catch (IllegalStateException e) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT, "예매할 수 없는 상태의 좌석입니다: seatId=" + seat.getId());
                }
            }

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
        } finally {
            if (!transactionActive) {
                unlockAll(acquiredLocks);
            }
        }
    }

    private void unlockAll(List<RLock> locks) {
        for (RLock lock : locks) {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
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