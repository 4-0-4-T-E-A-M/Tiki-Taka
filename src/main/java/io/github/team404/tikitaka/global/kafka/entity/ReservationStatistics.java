package io.github.team404.tikitaka.global.kafka.entity;

import io.github.team404.tikitaka.booking.entity.ReservationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "reservation_statistics")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservationStatistics {

    @Id
    @Column(name = "schedule_id", nullable = false)
    private Long scheduleId;

    @Column(name = "pending_payment_count", nullable = false)
    private Long pendingPaymentCount;

    @Column(name = "confirmed_count", nullable = false)
    private Long confirmedCount;

    @Column(name = "failed_count", nullable = false)
    private Long failedCount;

    @Column(name = "expired_count", nullable = false)
    private Long expiredCount;

    @Column(name = "canceled_count", nullable = false)
    private Long canceledCount;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private ReservationStatistics(
            Long scheduleId,
            Long pendingPaymentCount,
            Long confirmedCount,
            Long failedCount,
            Long expiredCount,
            Long canceledCount,
            LocalDateTime updatedAt
    ) {
        this.scheduleId = scheduleId;
        this.pendingPaymentCount = pendingPaymentCount;
        this.confirmedCount = confirmedCount;
        this.failedCount = failedCount;
        this.expiredCount = expiredCount;
        this.canceledCount = canceledCount;
        this.updatedAt = updatedAt;
    }

    public void increase(ReservationStatus status, LocalDateTime updatedAt) {
        switch (status) {
            case PENDING_PAYMENT -> this.pendingPaymentCount++;
            case CONFIRMED -> this.confirmedCount++;
            case FAILED -> this.failedCount++;
            case EXPIRED -> this.expiredCount++;
            case CANCELED -> this.canceledCount++;
        }
        this.updatedAt = updatedAt;
    }
}
