package io.github.team404.tikitaka.global.exception;

import io.github.team404.tikitaka.global.security.jwt.JwtErrorCode;
import io.github.team404.tikitaka.user.exception.UserErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessExceptionTest {

    @Test
    void 생성시_전달한_ErrorCode를_내부에_저장한다() {
        BusinessException exception = new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);

        assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    void getMessage는_ErrorCode의_getMessage와_같다() {
        BusinessException exception = new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);

        assertThat(exception.getMessage()).isEqualTo(CommonErrorCode.INTERNAL_SERVER_ERROR.getMessage());
    }

    @Test
    void 도메인별_ErrorCode도_동일하게_전달하고_보관한다() {
        BusinessException userException = new BusinessException(UserErrorCode.USER_NOT_FOUND);
        BusinessException jwtException = new BusinessException(JwtErrorCode.INVALID_TOKEN);

        assertThat(userException.getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND);
        assertThat(userException.getMessage()).isEqualTo("사용자를 찾을 수 없습니다.");

        assertThat(jwtException.getErrorCode()).isEqualTo(JwtErrorCode.INVALID_TOKEN);
        assertThat(jwtException.getMessage()).isEqualTo("유효하지 않은 토큰입니다.");
    }
}
