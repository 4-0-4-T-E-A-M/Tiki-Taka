package io.github.team404.tikitaka.performanceseat.entity;

public enum SeatStatus {
    AVAILABLE, // 예매 가능
    HELD, // 예매 진행 중 임시 홀드 (5분 TTL)
    RESERVED // 결제 확정
}