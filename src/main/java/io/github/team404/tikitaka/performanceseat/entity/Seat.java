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

// booking 도메인의 예매 생성 트랜잭션 경계를 위해 최소 필드만 우선 구현 (issue #13 참고, 좌석 도메인 상세는 신선우 담당)
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

    // 예매 취소/만료 시 다시 예매 가능 상태로 되돌림
    public void release() {
        this.status = SeatStatus.AVAILABLE;
    }
}