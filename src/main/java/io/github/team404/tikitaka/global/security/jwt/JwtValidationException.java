package io.github.team404.tikitaka.global.security.jwt;

import io.github.team404.tikitaka.global.exception.BusinessException;
import io.github.team404.tikitaka.global.exception.ErrorCode;

public class JwtValidationException extends BusinessException {

    public JwtValidationException(ErrorCode errorCode) {
        super(errorCode);
    }
}
