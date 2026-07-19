package io.github.team404.tikitaka.global.security.jwt;

import io.github.team404.tikitaka.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum JwtErrorCode implements ErrorCode {

    NULL_USER_ID(HttpStatus.UNAUTHORIZED, "AUTH_101", "userId는 null일 수 없습니다."),
    NOT_ACCESS_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_102", "Access Token이 아닙니다."),
    NOT_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_103", "Refresh Token이 아닙니다."),
    INVALID_USER_ID(HttpStatus.UNAUTHORIZED, "AUTH_104", "유효하지 않은 사용자 ID입니다."),
    EMPTY_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_105", "토큰이 비어 있습니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_106", "만료된 토큰입니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_107", "유효하지 않은 토큰입니다."),
    REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "AUTH_108", "Refresh Token이 없습니다."),
    OAUTH2_AUTHENTICATION_FAILED(HttpStatus.UNAUTHORIZED, "AUTH_109", "Google OAuth 인증에 실패했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
