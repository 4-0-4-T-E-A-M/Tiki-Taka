package io.github.team404.tikitaka.booking.entity;

public enum ReservationStatus {
    PENDING_PAYMENT, // 생성 직후, 결제 대기 중
    CONFIRMED, // 결제 성공, 예매 확정
    FAILED, // 결제 실패
    EXPIRED, // TTL 만료로 자동 만료
    CANCELED // 사용자에 의해 취소됨
}