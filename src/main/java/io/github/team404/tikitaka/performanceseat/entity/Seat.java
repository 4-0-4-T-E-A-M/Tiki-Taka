package io.github.team404.tikitaka.performanceseat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 상태 전이: AVAILABLE -> HELD(hold, 5분 TTL) -> RESERVED(confirm, 결제 확정) / HELD·RESERVED -> AVAILABLE(release, 취소·만료 복구)
@Entity
@Table(name = "seats")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "seat_id")
    private Long id; // 좌석 PK

    @Column(name = "section_id", nullable = false)
    private Long sectionId; // 소속 구역(Section) FK — Section 엔티티 미구현으로 순수 id만 보관

    @Column(name = "row_name", nullable = false)
    private String rowName; // 열 이름 (예: "A")

    @Column(name = "seat_number", nullable = false)
    private Integer seatNumber; // 좌석 번호

    @Enumerated(EnumType.STRING)
    @Column(name = "grade", nullable = false)
    private SeatGrade grade; // 좌석 등급

    @Column(name = "price", nullable = false)
    private Integer price; // 좌석 가격

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SeatStatus status; // 좌석 상태

    @Column(name = "seat_score")
    private Integer seatScore; // 인기 좌석 점수 (선택)

    @Column(name = "priority_rank")
    private Integer priorityRank; // 좌석 우선순위 (선택)

    @Version
    @Column(name = "version", nullable = false)
    private Long version; // 동시 홀드 충돌 감지용 낙관적 락 버전

    @Builder
    private Seat(Long sectionId, String rowName, Integer seatNumber, SeatGrade grade, Integer price) {
        this.sectionId = sectionId;
        this.rowName = rowName;
        this.seatNumber = seatNumber;
        this.grade = grade;
        this.price = price;
        this.status = SeatStatus.AVAILABLE;
    }

    // 예매 생성 시 임시 홀드 (AVAILABLE만 가능, RESERVED 확정 전환은 결제 확정 단계에서 처리)
    public void hold() {
        if (status != SeatStatus.AVAILABLE) {
            throw new IllegalStateException("AVAILABLE 상태의 좌석만 홀드할 수 있습니다. 현재 상태: " + status);
        }
        this.status = SeatStatus.HELD;
    }

    // 결제 확정 시 홀드를 확정 상태로 전이 (HELD만 가능)
    public void confirm() {
        if (status != SeatStatus.HELD) {
            throw new IllegalStateException("HELD 상태의 좌석만 확정할 수 있습니다. 현재 상태: " + status);
        }
        this.status = SeatStatus.RESERVED;
    }

    // 예매 취소/만료 시 다시 예매 가능 상태로 되돌림 (HELD 또는 RESERVED만 가능)
    public void release() {
        if (status != SeatStatus.HELD && status != SeatStatus.RESERVED) {
            throw new IllegalStateException("HELD 또는 RESERVED 상태의 좌석만 해제할 수 있습니다. 현재 상태: " + status);
        }
        this.status = SeatStatus.AVAILABLE;
    }
}