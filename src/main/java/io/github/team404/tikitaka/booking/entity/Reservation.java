package io.github.team404.tikitaka.booking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// user/schedule/simulation 엔티티가 아직 없어 연관관계 대신 FK id만 보관
// 단일 컬럼 인덱스 5개는 #70 EXPLAIN 분석에서 실측 검증됨(docs/perf/reservation-search-explain-analysis.md).
// docs/db/ddl.sql에도 같은 인덱스가 문서화되어 있었지만 여기 선언이 없어 ddl-auto=update가
// 실제로는 만들지 않고 있었음(PK만 존재) — 이 애노테이션이 그 갭을 메운다.
@Entity
@Table(name = "reservations", indexes = {
        @Index(name = "idx_reservations_user_id", columnList = "user_id"),
        @Index(name = "idx_reservations_simulation_id", columnList = "simulation_id"),
        @Index(name = "idx_reservations_schedule_id", columnList = "schedule_id"),
        @Index(name = "idx_reservations_status", columnList = "status"),
        @Index(name = "idx_reservations_created_at", columnList = "created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reservation_id")
    private Long id; // 예매 PK

    @Column(name = "user_id", nullable = false)
    private Long userId; // 예매한 유저 FK (User 엔티티 미구현으로 순수 id만 보관)

    @Column(name = "simulation_id", nullable = false)
    private Long simulationId; // 이 예매가 발생한 연습 세션(Simulation) FK

    @Column(name = "schedule_id", nullable = false)
    private Long scheduleId; // 공연 회차(PerformanceSchedule) FK

    @Column(name = "selected_quantity", nullable = false)
    private Integer selectedQuantity; // 선택한 좌석 수량

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ReservationStatus status; // 예매 상태 (PENDING_PAYMENT/CONFIRMED/FAILED/EXPIRED/CANCELED)

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt; // 예매 생성 시각

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt; // 결제 확정 시각 (미확정이면 null)

    @Column(name = "expired_at")
    private LocalDateTime expiredAt; // TTL 만료로 자동 만료 처리된 시각 (해당 없으면 null)

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt; // 취소 처리 시각 (미취소면 null)

    @Builder
    private Reservation(Long userId, Long simulationId, Long scheduleId, Integer selectedQuantity) {
        this.userId = userId;
        this.simulationId = simulationId;
        this.scheduleId = scheduleId;
        this.selectedQuantity = selectedQuantity;
        this.status = ReservationStatus.PENDING_PAYMENT;
        this.createdAt = LocalDateTime.now();
    }

    // 결제 성공 시 확정 처리
    public void confirm() {
        requireStatus(ReservationStatus.PENDING_PAYMENT);
        this.status = ReservationStatus.CONFIRMED;
        this.confirmedAt = LocalDateTime.now();
    }

    // 결제 실패 처리
    public void fail() {
        requireStatus(ReservationStatus.PENDING_PAYMENT);
        this.status = ReservationStatus.FAILED;
    }

    // 결제 대기 TTL 만료 처리
    public void expire() {
        requireStatus(ReservationStatus.PENDING_PAYMENT);
        this.status = ReservationStatus.EXPIRED;
        this.expiredAt = LocalDateTime.now();
    }

    // 결제 전(PENDING_PAYMENT) 또는 확정 후(CONFIRMED) 취소 처리
    public void cancel() {
        if (status != ReservationStatus.PENDING_PAYMENT && status != ReservationStatus.CONFIRMED) {
            throw new IllegalStateException(
                    "PENDING_PAYMENT 또는 CONFIRMED 상태에서만 취소할 수 있습니다. 현재 상태: " + status);
        }
        this.status = ReservationStatus.CANCELED;
        this.canceledAt = LocalDateTime.now();
    }

    // 현재 상태가 기대 상태와 다르면 전이를 거부
    private void requireStatus(ReservationStatus expected) {
        if (this.status != expected) {
            throw new IllegalStateException(
                    expected + " 상태에서만 가능한 전이입니다. 현재 상태: " + this.status);
        }
    }
}