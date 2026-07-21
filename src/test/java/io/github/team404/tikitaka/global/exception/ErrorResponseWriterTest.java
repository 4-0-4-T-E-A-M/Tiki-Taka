package io.github.team404.tikitaka.global.exception;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.team404.tikitaka.user.exception.UserErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ErrorResponseWriter가 실제로 쓰는 HTTP 응답(상태 코드, Content-Type, 인코딩, JSON 바디)을
 * 다른 클래스(EntryPoint 등)를 거치지 않고 직접 검증한다.
 */
class ErrorResponseWriterTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final ErrorResponseWriter writer = new ErrorResponseWriter(objectMapper);

    @Test
    void ErrorCode에_정의된_HTTP_상태를_그대로_응답에_설정한다() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.write(response, CommonErrorCode.ACCESS_DENIED);

        assertThat(response.getStatus()).isEqualTo(CommonErrorCode.ACCESS_DENIED.getHttpStatus().value());
    }

    @Test
    void Content_Type은_application_json이고_인코딩은_UTF_8이다() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.write(response, CommonErrorCode.UNAUTHORIZED);

        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getCharacterEncoding()).isEqualToIgnoringCase(StandardCharsets.UTF_8.name());
    }

    @Test
    void JSON_바디의_code와_message가_ErrorCode와_정확히_일치한다() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.write(response, UserErrorCode.USER_NOT_FOUND);

        JsonNode json = objectMapper.readTree(response.getContentAsString());
        assertThat(json.get("code").asText()).isEqualTo("USER_001");
        assertThat(json.get("message").asText()).isEqualTo("사용자를 찾을 수 없습니다.");
        assertThat(json.has("timestamp")).isTrue();
        assertThat(json.get("timestamp").isNull()).isFalse();
    }

    @Test
    void 한글_메시지가_바이트_단위로도_깨지지_않는다() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.write(response, CommonErrorCode.INVALID_INPUT_VALUE);

        String decoded = new String(response.getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(decoded).contains("잘못된 입력값입니다.");
    }
}
