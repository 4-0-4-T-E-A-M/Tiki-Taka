package io.github.team404.tikitaka.performanceseat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 공연(Performance)과는 다른 애그리거트라 연관관계 대신 FK id만 보관 (Reservation/Seat와 동일 원칙)
@Entity
@Table(name = "performance_schedules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PerformanceSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "schedule_id")
    private Long id; // 회차 PK

    @Column(name = "performance_id", nullable = false)
    private Long performanceId; // 소속 공연(Performance) FK

    @Column(name = "performance_datetime", nullable = false)
    private LocalDateTime performanceDatetime; // 회차 공연 일시

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ScheduleStatus status; // 회차 상태 — 오픈 전환은 5주차 공연 오픈 스케줄러 이슈 소관

    @Builder
    private PerformanceSchedule(Long performanceId, LocalDateTime performanceDatetime) {
        this.performanceId = performanceId;
        this.performanceDatetime = performanceDatetime;
        this.status = ScheduleStatus.SCHEDULED;
    }
}
