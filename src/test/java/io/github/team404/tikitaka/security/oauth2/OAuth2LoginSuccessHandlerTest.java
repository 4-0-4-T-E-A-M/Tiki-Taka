package io.github.team404.tikitaka.security.oauth2;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.team404.tikitaka.global.security.jwt.JwtTokenProvider;
import io.github.team404.tikitaka.global.security.oauth2.CustomOAuth2User;
import io.github.team404.tikitaka.global.security.oauth2.OAuth2LoginSuccessHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class OAuth2LoginSuccessHandlerTest {

    private static final Long USER_ID = 1L;
    private static final String ACCESS_TOKEN = "test-access-token";
    private static final String REFRESH_TOKEN = "test-refresh-token";

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    private OAuth2LoginSuccessHandler successHandler;

    @BeforeEach
    void setUp() {
        successHandler = new OAuth2LoginSuccessHandler(jwtTokenProvider);
    }

    @Test
    void CustomOAuth2User의_userId로_Access_Token과_Refresh_Token을_발급한다() throws Exception {
        // given
        given(jwtTokenProvider.generateAccessToken(USER_ID)).willReturn(ACCESS_TOKEN);
        given(jwtTokenProvider.generateRefreshToken(USER_ID)).willReturn(REFRESH_TOKEN);
        given(jwtTokenProvider.getAccessTokenExpiry()).willReturn(3_600_000L);
        given(jwtTokenProvider.getRefreshTokenExpiry()).willReturn(604_800_000L);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        Authentication authentication = authenticationOf(customOAuth2User(USER_ID));

        // when
        successHandler.onAuthenticationSuccess(request, response, authentication);

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = new ObjectMapper().readValue(response.getContentAsString(), Map.class);
        assertThat(body.get("accessToken")).isEqualTo(ACCESS_TOKEN);
        assertThat(body).doesNotContainKey("refreshToken");
    }

    @Test
    void Refresh_Token은_HttpOnly_쿠키로만_전달되고_URL이나_바디에_노출되지_않는다() throws Exception {
        // given
        given(jwtTokenProvider.generateAccessToken(USER_ID)).willReturn(ACCESS_TOKEN);
        given(jwtTokenProvider.generateRefreshToken(USER_ID)).willReturn(REFRESH_TOKEN);
        given(jwtTokenProvider.getAccessTokenExpiry()).willReturn(3_600_000L);
        given(jwtTokenProvider.getRefreshTokenExpiry()).willReturn(604_800_000L);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        Authentication authentication = authenticationOf(customOAuth2User(USER_ID));

        // when
        successHandler.onAuthenticationSuccess(request, response, authentication);

        // then
        String setCookieHeader = response.getHeader("Set-Cookie");
        assertThat(setCookieHeader).isNotNull();
        assertThat(setCookieHeader)
                .contains("refreshToken=" + REFRESH_TOKEN)
                .containsIgnoringCase("HttpOnly");
        assertThat(response.getContentAsString()).doesNotContain(REFRESH_TOKEN);
        assertThat(response.getRedirectedUrl()).isNull();
    }

    @Test
    void 예상하지_못한_Principal_타입이면_500을_응답하고_토큰을_발급하지_않는다() throws Exception {
        // given
        OAuth2User unexpectedOAuth2User = new DefaultOAuth2User(
                List.of(), Map.of("sub", "some-id"), "sub");
        Authentication authentication = authenticationOf(unexpectedOAuth2User);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        successHandler.onAuthenticationSuccess(request, response, authentication);

        // then
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getContentAsString()).contains("오류");
    }

    private CustomOAuth2User customOAuth2User(Long userId) {
        DefaultOAuth2User delegate = new DefaultOAuth2User(
                List.of(), Map.of("sub", "google-sub-123"), "sub");
        return new CustomOAuth2User(delegate, userId);
    }

    private Authentication authenticationOf(OAuth2User principal) {
        return new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
    }
}
