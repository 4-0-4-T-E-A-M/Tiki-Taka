package io.github.team404.tikitaka.security.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.team404.tikitaka.global.exception.ErrorResponseWriter;
import io.github.team404.tikitaka.global.security.jwt.JwtAccessDeniedHandler;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAccessDeniedHandlerTest {

    private final JwtAccessDeniedHandler accessDeniedHandler =
            new JwtAccessDeniedHandler(new ErrorResponseWriter(new ObjectMapper().findAndRegisterModules()));

    @Test
    void 권한이_부족하면_403과_공통_오류_응답_형식을_반환한다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        accessDeniedHandler.handle(request, response, new AccessDeniedException("무시됨"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/json");
        String body = response.getContentAsString();
        assertThat(body).contains("\"code\":\"AUTH_002\"");
        assertThat(body).contains("\"message\":\"접근 권한이 없습니다.\"");
        assertThat(body).contains("\"timestamp\"");
    }
}
