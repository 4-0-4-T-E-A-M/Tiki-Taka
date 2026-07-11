package io.github.team404.tikitaka.performanceseat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 회차(PerformanceSchedule)와는 다른 애그리거트라 연관관계 대신 FK id만 보관 (Reservation/Seat와 동일 원칙)
@Entity
@Table(name = "sections")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Section {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "section_id")
    private Long id; // 구역 PK

    @Column(name = "schedule_id", nullable = false)
    private Long scheduleId; // 소속 회차(PerformanceSchedule) FK

    @Column(name = "name", nullable = false)
    private String name; // 구역 이름 (예: "1층 A구역")

    @Column(name = "total_seat_count", nullable = false)
    private Integer totalSeatCount;

    @Column(name = "remaining_seat_count", nullable = false)
    private Integer remainingSeatCount;

    @Builder
    private Section(Long scheduleId, String name, Integer totalSeatCount) {
        this.scheduleId = scheduleId;
        this.name = name;
        this.totalSeatCount = totalSeatCount;
        this.remainingSeatCount = totalSeatCount;
    }
}
