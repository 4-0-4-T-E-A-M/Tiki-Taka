package io.github.team404.tikitaka.booking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// reservation/seat 모두 다른 애그리거트라 연관관계 대신 FK id만 보관 (Reservation과 동일 원칙)
@Entity
@Table(name = "reservation_seats")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservationSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reservation_seat_id")
    private Long id; // 예매-좌석 매핑 PK

    @Column(name = "reservation_id", nullable = false)
    private Long reservationId; // 예매(Reservation) FK

    @Column(name = "seat_id", nullable = false)
    private Long seatId; // 좌석(Seat) FK

    @Column(name = "hold_started_at")
    private LocalDateTime holdStartedAt; // 임시 홀드 시작 시각

    @Column(name = "hold_expires_at")
    private LocalDateTime holdExpiresAt; // 임시 홀드 만료 시각 (TTL)

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt; // 결제 확정 시각 (미확정이면 null)

    @Column(name = "released_at")
    private LocalDateTime releasedAt; // 취소/만료로 좌석이 풀린 시각 (해당 없으면 null)

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt; // 매핑 생성 시각

    @Builder
    private ReservationSeat(Long reservationId, Long seatId, LocalDateTime holdExpiresAt) {
        this.reservationId = reservationId;
        this.seatId = seatId;
        this.holdStartedAt = LocalDateTime.now();
        this.holdExpiresAt = holdExpiresAt;
        this.createdAt = LocalDateTime.now();
    }

    // 취소/만료로 좌석 홀드를 해제
    public void release() {
        this.releasedAt = LocalDateTime.now();
    }
}