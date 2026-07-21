package io.github.team404.tikitaka.security.oauth2;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.team404.tikitaka.global.exception.ErrorResponseWriter;
import io.github.team404.tikitaka.global.security.jwt.JwtErrorCode;
import io.github.team404.tikitaka.global.security.oauth2.OAuth2LoginFailureHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class OAuth2LoginFailureHandlerTest {

    private final OAuth2LoginFailureHandler handler =
            new OAuth2LoginFailureHandler(new ErrorResponseWriter(new ObjectMapper().findAndRegisterModules()));

    @Test
    void OAuth2_로그인_실패시_JwtErrorCode에_정의된_상태와_공통_형식으로_응답한다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        OAuth2AuthenticationException exception =
                new OAuth2AuthenticationException(new OAuth2Error("access_denied"), "실제 원인은 응답에 노출되면 안 됨");

        handler.onAuthenticationFailure(request, response, exception);

        assertThat(response.getStatus()).isEqualTo(JwtErrorCode.OAUTH2_AUTHENTICATION_FAILED.getHttpStatus().value());
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getCharacterEncoding()).isEqualToIgnoringCase(StandardCharsets.UTF_8.name());

        String body = response.getContentAsString();
        assertThat(body).contains("\"code\":\"" + JwtErrorCode.OAUTH2_AUTHENTICATION_FAILED.getCode() + "\"");
        assertThat(body).contains("\"message\":\"" + JwtErrorCode.OAUTH2_AUTHENTICATION_FAILED.getMessage() + "\"");
        assertThat(body).contains("\"timestamp\"");
        assertThat(body).doesNotContain("실제 원인은 응답에 노출되면 안 됨");
    }
}
