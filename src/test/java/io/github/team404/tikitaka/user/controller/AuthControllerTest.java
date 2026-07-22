package io.github.team404.tikitaka.user.controller;

import io.github.team404.tikitaka.global.exception.JwtValidationException;
import io.github.team404.tikitaka.global.security.jwt.JwtTokenProvider;
import io.github.team404.tikitaka.user.service.UserSignupService;
import io.github.team404.tikitaka.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AuthController(/api/auth/refresh)의 컨트롤러 로직만 검증한다.
 * SecurityFilterChain을 거치지 않도록 필터를 비활성화해(addFilters = false) 순수 비즈니스 로직에 집중하고,
 * Security 경로 접근 제어 자체는 SecurityFilterChainTest에서 별도로 검증한다.
 */
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    private static final String COOKIE_NAME = "refreshToken";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserSignupService userSignupService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void 정상_Refresh_Token이면_새_Access_Token을_발급한다() throws Exception {
        given(jwtTokenProvider.getUserIdFromToken("valid-refresh")).willReturn(1L);
        given(userSignupService.existsById(1L)).willReturn(true);
        given(jwtTokenProvider.generateAccessToken(1L)).willReturn("new-access-token");
        given(jwtTokenProvider.getAccessTokenExpiry()).willReturn(3_600_000L);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie(COOKIE_NAME, "valid-refresh")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessTokenExpiresIn").value(3600));
    }

    @Test
    void 쿠키가_없으면_401을_반환하고_토큰_검증을_시도하지_않는다() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Refresh Token이 없습니다."));
    }

    @Test
    void 빈_쿠키값이면_401을_반환한다() throws Exception {
        willThrow(new JwtValidationException("토큰이 비어 있습니다."))
                .given(jwtTokenProvider).validateRefreshToken("");

        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie(COOKIE_NAME, "")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("토큰이 비어 있습니다."));
    }

    @Test
    void Access_Token을_refresh_쿠키로_전달하면_401을_반환한다() throws Exception {
        willThrow(new JwtValidationException("Refresh Token이 아닙니다."))
                .given(jwtTokenProvider).validateRefreshToken("an-access-token");

        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie(COOKIE_NAME, "an-access-token")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Refresh Token이 아닙니다."));
    }

    @Test
    void 만료된_Refresh_Token이면_401을_반환한다() throws Exception {
        willThrow(new JwtValidationException("만료된 토큰입니다."))
                .given(jwtTokenProvider).validateRefreshToken("expired-refresh");

        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie(COOKIE_NAME, "expired-refresh")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("만료된 토큰입니다."));
    }

    @Test
    void 변조된_Refresh_Token이면_401을_반환한다() throws Exception {
        willThrow(new JwtValidationException("유효하지 않은 토큰입니다."))
                .given(jwtTokenProvider).validateRefreshToken("tampered-refresh");

        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie(COOKIE_NAME, "tampered-refresh")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("유효하지 않은 토큰입니다."));
    }

    @Test
    void 존재하지_않는_사용자면_401을_반환하고_새_토큰을_발급하지_않는다() throws Exception {
        given(jwtTokenProvider.getUserIdFromToken("ghost-user-refresh")).willReturn(999L);
        given(userSignupService.existsById(999L)).willReturn(false);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie(COOKIE_NAME, "ghost-user-refresh")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("존재하지 않는 사용자입니다."));
    }
}
