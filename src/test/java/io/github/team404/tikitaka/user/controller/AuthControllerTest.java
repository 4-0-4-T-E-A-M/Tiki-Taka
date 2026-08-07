package io.github.team404.tikitaka.user.controller;

import io.github.team404.tikitaka.global.exception.BusinessException;
import io.github.team404.tikitaka.global.exception.ErrorResponseWriter;
import io.github.team404.tikitaka.global.security.jwt.JwtErrorCode;
import io.github.team404.tikitaka.global.security.jwt.JwtTokenProvider;
import io.github.team404.tikitaka.global.security.jwt.JwtValidationException;
import io.github.team404.tikitaka.user.domain.OAuthProvider;
import io.github.team404.tikitaka.user.domain.User;
import io.github.team404.tikitaka.user.domain.UserRole;
import io.github.team404.tikitaka.user.exception.UserErrorCode;
import io.github.team404.tikitaka.user.service.UserSignupService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.Optional;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AuthController(/api/auth/refresh)의 컨트롤러 로직만 검증한다.
 * SecurityFilterChain을 거치지 않도록 필터를 비활성화해(addFilters = false) 순수 비즈니스 로직에 집중하고,
 * Security 경로 접근 제어 자체는 SecurityFilterChainTest에서 별도로 검증한다.
 * SecurityConfig가 @WebMvcTest 슬라이스에 함께 로드되며 JwtAuthenticationFilter 등이
 * ErrorResponseWriter를 필요로 하므로 명시적으로 Import한다.
 */
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ErrorResponseWriter.class)
class AuthControllerTest {

    private static final String COOKIE_NAME = "refreshToken";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserSignupService userSignupService;

    @Test
    void 정상_Refresh_Token이면_새_Access_Token을_발급한다() throws Exception {
        User user = new User("test@example.com", "테스트", OAuthProvider.GOOGLE, "provider-id");
        given(jwtTokenProvider.getUserIdFromToken("valid-refresh")).willReturn(1L);
        given(userSignupService.findById(1L)).willReturn(Optional.of(user));
        given(jwtTokenProvider.generateAccessToken(1L, UserRole.USER)).willReturn("new-access-token");
        given(jwtTokenProvider.getAccessTokenExpiry()).willReturn(3_600_000L);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie(COOKIE_NAME, "valid-refresh")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("액세스 토큰 재발급에 성공했습니다."))
                .andExpect(jsonPath("$.data.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.accessTokenExpiresIn").value(3600))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.tokenType").doesNotExist())
                .andExpect(jsonPath("$.accessTokenExpiresIn").doesNotExist());
    }

    @Test
    void 쿠키가_없으면_401을_반환하고_토큰_검증을_시도하지_않는다() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_108"))
                .andExpect(jsonPath("$.message").value("Refresh Token이 없습니다."))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.timestamp").doesNotExist());
    }

    @Test
    void 빈_쿠키값이면_401을_반환한다() throws Exception {
        willThrow(new JwtValidationException(JwtErrorCode.EMPTY_TOKEN))
                .given(jwtTokenProvider).validateRefreshToken("");

        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie(COOKIE_NAME, "")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_105"))
                .andExpect(jsonPath("$.message").value("토큰이 비어 있습니다."))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.timestamp").doesNotExist());
    }

    @Test
    void Access_Token을_refresh_쿠키로_전달하면_401을_반환한다() throws Exception {
        willThrow(new JwtValidationException(JwtErrorCode.NOT_REFRESH_TOKEN))
                .given(jwtTokenProvider).validateRefreshToken("an-access-token");

        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie(COOKIE_NAME, "an-access-token")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_103"))
                .andExpect(jsonPath("$.message").value("Refresh Token이 아닙니다."))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.timestamp").doesNotExist());
    }

    @Test
    void 만료된_Refresh_Token이면_401을_반환한다() throws Exception {
        willThrow(new JwtValidationException(JwtErrorCode.EXPIRED_TOKEN))
                .given(jwtTokenProvider).validateRefreshToken("expired-refresh");

        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie(COOKIE_NAME, "expired-refresh")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_106"))
                .andExpect(jsonPath("$.message").value("만료된 토큰입니다."))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.timestamp").doesNotExist());
    }

    @Test
    void 변조된_Refresh_Token이면_401을_반환한다() throws Exception {
        willThrow(new JwtValidationException(JwtErrorCode.INVALID_TOKEN))
                .given(jwtTokenProvider).validateRefreshToken("tampered-refresh");

        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie(COOKIE_NAME, "tampered-refresh")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_107"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 토큰입니다."))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.timestamp").doesNotExist());
    }

    @Test
    void 존재하지_않는_사용자면_404를_반환하고_새_토큰을_발급하지_않는다() throws Exception {
        given(jwtTokenProvider.getUserIdFromToken("ghost-user-refresh")).willReturn(999L);
        given(userSignupService.findById(999L)).willReturn(Optional.empty());

        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie(COOKIE_NAME, "ghost-user-refresh")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_001"))
                .andExpect(jsonPath("$.message").value("사용자를 찾을 수 없습니다."))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.timestamp").doesNotExist());
    }
}
