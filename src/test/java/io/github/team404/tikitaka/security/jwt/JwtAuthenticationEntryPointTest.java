package io.github.team404.tikitaka.security.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.team404.tikitaka.global.exception.ErrorResponseWriter;
import io.github.team404.tikitaka.global.security.jwt.JwtAuthenticationEntryPoint;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthenticationEntryPointTest {

    private final JwtAuthenticationEntryPoint entryPoint =
            new JwtAuthenticationEntryPoint(new ErrorResponseWriter(new ObjectMapper().findAndRegisterModules()));

    @Test
    void 인증되지_않은_요청이면_401과_공통_오류_응답_형식을_반환한다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new BadCredentialsException("무시됨"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/json");
        String body = response.getContentAsString();
        assertThat(body).contains("\"code\":\"AUTH_001\"");
        assertThat(body).contains("\"message\":\"인증이 필요합니다.\"");
        assertThat(body).contains("\"data\":null");
        assertThat(body).doesNotContain("\"timestamp\"");
    }
}
