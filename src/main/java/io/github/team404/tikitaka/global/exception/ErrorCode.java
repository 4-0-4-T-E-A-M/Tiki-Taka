package io.github.team404.tikitaka.global.exception;

import org.springframework.http.HttpStatus;

public interface ErrorCode {

    HttpStatus getHttpStatus();

    String getCode();

    String getMessage();
}
