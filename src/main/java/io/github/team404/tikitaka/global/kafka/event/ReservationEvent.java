package io.github.team404.tikitaka.global.kafka.event;

import io.github.team404.tikitaka.booking.entity.ReservationStatus;
import java.time.LocalDateTime;
import java.util.UUID;

// 스키마 버전 정책(v1, eventId를 포함한 필드 6개 기준): 하위 호환을 위해 필드 추가는 optional/nullable로만 허용,
// 필드 의미 변경·삭제처럼 깨지는 변경은 이 record를 고치지 말고 새 이벤트 타입(예: ReservationEventV2)과
// 새 토픽으로 분리한다 — 기존 컨슈머(조준형, #92 Notes 참고)가 구버전 이벤트를 계속 읽을 수 있어야 한다.
public record ReservationEvent(
        UUID eventId,
        Long reservationId,
        Long userId,
        Long scheduleId,
        ReservationStatus status,
        LocalDateTime occurredAt
) {
}
