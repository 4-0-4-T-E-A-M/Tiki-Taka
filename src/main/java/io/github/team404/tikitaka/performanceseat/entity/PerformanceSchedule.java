package io.github.team404.tikitaka.performanceseat.entity;

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

// 공연(Performance)과는 다른 애그리거트라 연관관계 대신 FK id만 보관 (Reservation/Seat와 동일 원칙)
// performance_datetime / performance_id / status 인덱스는 #73 인덱스 설계 결정에서 그대로 유지하기로 함
// (docs/tradeoffs/database/performance-search-index-design.md).
// docs/db/ddl.sql에 문서화돼 있었지만 여기 선언이 없어 ddl-auto=update가 실제로는 만들지 않고
// 있었음(PK만 존재) — 이 애노테이션이 그 갭을 메운다.
@Entity
@Table(name = "performance_schedules", indexes = {
        @Index(name = "idx_performance_schedules_datetime", columnList = "performance_datetime"),
        @Index(name = "idx_performance_schedules_performance_id", columnList = "performance_id"),
        @Index(name = "idx_performance_schedules_status", columnList = "status")
})
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

    // 예매 오픈 시각. 공연 일시(performanceDatetime)와 별개 — 실제 티켓팅처럼 공연보다 며칠~몇 주 앞서 열린다.
    // 이 시각이 지나면 공연 오픈 스케줄러(#75)가 SCHEDULED → OPEN으로 전환한다.
    @Column(name = "open_at", nullable = false)
    private LocalDateTime openAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ScheduleStatus status; // 회차 상태 — 오픈 전환은 공연 오픈 스케줄러(#75)가 담당

    @Builder
    private PerformanceSchedule(Long performanceId, LocalDateTime performanceDatetime, LocalDateTime openAt) {
        this.performanceId = performanceId;
        this.performanceDatetime = performanceDatetime;
        this.openAt = openAt;
        this.status = ScheduleStatus.SCHEDULED;
    }

    // 오픈 시각 도달 시 예매 가능 상태로 전환한다. 스케줄러가 반복 실행되거나 다중 인스턴스가
    // 같은 회차를 동시에 집어도 안전하도록, 이미 OPEN이면 아무 것도 하지 않고 false를 돌려준다
    // (호출 측은 이 반환값으로 "이번에 실제로 열렸는지"를 구분해 대기열 입장 허용 초기화를 한 번만 한다).
    // CLOSED·CANCELED에서 다시 열리는 전이는 허용하지 않는다.
    public boolean open() {
        if (status == ScheduleStatus.OPEN) {
            return false;
        }
        if (status != ScheduleStatus.SCHEDULED) {
            throw new IllegalStateException("SCHEDULED 상태의 회차만 오픈할 수 있습니다. 현재 상태: " + status);
        }
        this.status = ScheduleStatus.OPEN;
        return true;
    }

    // 예매 진입 가능 여부의 단일 기준. 좌석에 별도의 오픈 전 상태를 두는 대신(#75 설계 결정)
    // 회차 상태 하나로 판단한다 — 실제 예매 트랜잭션 진입점의 검증은 booking 도메인 소관.
    public boolean isBookable() {
        return status == ScheduleStatus.OPEN;
    }
}
