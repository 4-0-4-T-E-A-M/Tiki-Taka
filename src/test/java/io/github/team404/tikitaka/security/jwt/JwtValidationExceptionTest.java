package io.github.team404.tikitaka.security.jwt;

import io.github.team404.tikitaka.global.exception.BusinessException;
import io.github.team404.tikitaka.global.security.jwt.JwtErrorCode;
import io.github.team404.tikitaka.global.security.jwt.JwtValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtValidationExceptionTest {

    @Test
    void BusinessException을_상속한다() {
        JwtValidationException exception = new JwtValidationException(JwtErrorCode.INVALID_TOKEN);

        assertThat(exception).isInstanceOf(BusinessException.class);
    }

    @Test
    void 생성시_전달한_JwtErrorCode를_보관한다() {
        JwtValidationException exception = new JwtValidationException(JwtErrorCode.EXPIRED_TOKEN);

        assertThat(exception.getErrorCode()).isEqualTo(JwtErrorCode.EXPIRED_TOKEN);
    }

    @Test
    void 예외_메시지는_JwtErrorCode의_getMessage와_같다() {
        JwtValidationException exception = new JwtValidationException(JwtErrorCode.NOT_REFRESH_TOKEN);

        assertThat(exception.getMessage()).isEqualTo(JwtErrorCode.NOT_REFRESH_TOKEN.getMessage());
    }
}
