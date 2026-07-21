package io.github.team404.tikitaka.global.exception;

import io.github.team404.tikitaka.global.security.jwt.JwtErrorCode;
import io.github.team404.tikitaka.user.exception.UserErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorResponseTest {

    @Test
    void from은_ErrorCode의_code와_message를_그대로_담고_timestamp를_채운다() {
        ErrorResponse response = ErrorResponse.from(CommonErrorCode.INVALID_INPUT_VALUE);

        assertThat(response.code()).isEqualTo(CommonErrorCode.INVALID_INPUT_VALUE.getCode());
        assertThat(response.message()).isEqualTo(CommonErrorCode.INVALID_INPUT_VALUE.getMessage());
        assertThat(response.timestamp()).isNotNull();
    }

    @Test
    void from은_도메인별_ErrorCode에도_동일하게_동작한다() {
        ErrorResponse userResponse = ErrorResponse.from(UserErrorCode.USER_NOT_FOUND);
        ErrorResponse jwtResponse = ErrorResponse.from(JwtErrorCode.EXPIRED_TOKEN);

        assertThat(userResponse.code()).isEqualTo("USER_001");
        assertThat(userResponse.message()).isEqualTo("사용자를 찾을 수 없습니다.");
        assertThat(userResponse.timestamp()).isNotNull();

        assertThat(jwtResponse.code()).isEqualTo("AUTH_106");
        assertThat(jwtResponse.message()).isEqualTo("만료된 토큰입니다.");
        assertThat(jwtResponse.timestamp()).isNotNull();
    }

    @Test
    void of는_전달한_code와_message를_그대로_담고_timestamp를_채운다() {
        ErrorResponse response = ErrorResponse.of("COMMON_001", "이름은 비어 있을 수 없습니다.");

        assertThat(response.code()).isEqualTo("COMMON_001");
        assertThat(response.message()).isEqualTo("이름은 비어 있을 수 없습니다.");
        assertThat(response.timestamp()).isNotNull();
    }
}
