package io.github.team404.tikitaka.performanceseat.exception;

import io.github.team404.tikitaka.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum QueueErrorCode implements ErrorCode {

    SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "QUEUE_001", "존재하지 않는 공연 회차입니다."),
    QUEUE_ENTRY_NOT_FOUND(HttpStatus.NOT_FOUND, "QUEUE_002", "대기열에 진입하지 않은 사용자입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
