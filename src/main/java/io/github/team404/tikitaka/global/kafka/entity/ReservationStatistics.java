package io.github.team404.tikitaka.global.kafka.entity;

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

    @Column(name = "confirmed_count", nullable = false)
    private Long confirmedCount;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private ReservationStatistics(Long scheduleId, Long confirmedCount, LocalDateTime updatedAt) {
        this.scheduleId = scheduleId;
        this.confirmedCount = confirmedCount;
        this.updatedAt = updatedAt;
    }

    public void increaseConfirmedCount(LocalDateTime updatedAt) {
        this.confirmedCount++;
        this.updatedAt = updatedAt;
    }
}
