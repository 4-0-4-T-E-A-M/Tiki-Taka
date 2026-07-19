package io.github.team404.tikitaka.global.security.oauth2;

import io.github.team404.tikitaka.global.security.jwt.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

// 토큰 전달 방식(6-5 정책):
// - Access Token은 응답 바디로만 전달한다(URL 노출 없음). 클라이언트가 메모리에 보관하고
//   Authorization: Bearer 헤더로 재사용하는 것을 전제로 한다.
// - Refresh Token은 HttpOnly 쿠키로만 전달하고 응답 바디에는 포함하지 않는다.
//   Secure 속성은 요청이 HTTPS로 왔는지 여부(request.isSecure())를 그대로 반영해
//   로컬 HTTP 개발 환경과 운영 HTTPS 환경 모두에서 쿠키가 정상 동작하도록 한다.
// - 프론트엔드 콜백 리다이렉트 계약이 아직 확정되지 않아, 이번 이슈에서는 콜백 응답을
//   JSON 바디로 직접 내려주는 임시 구현으로 처리한다. 프론트엔드 연동이 확정되면
//   authorization code 교환 방식 등으로 재검토가 필요하다.
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                         HttpServletResponse response,
                                         Authentication authentication) throws IOException {
        OAuth2User principal = (OAuth2User) authentication.getPrincipal();
        if (!(principal instanceof CustomOAuth2User customOAuth2User)) {
            log.error("예상하지 못한 Principal 타입입니다: {}", principal.getClass().getName());
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"message\":\"인증 처리 중 오류가 발생했습니다.\"}");
            return;
        }

        Long userId = customOAuth2User.getUserId();
        String accessToken = jwtTokenProvider.generateAccessToken(userId);
        String refreshToken = jwtTokenProvider.generateRefreshToken(userId);

        ResponseCookie refreshTokenCookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, refreshToken)
                .httpOnly(true)
                .secure(request.isSecure())
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(Duration.ofMillis(jwtTokenProvider.getRefreshTokenExpiry()))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString());

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(String.format(
                "{\"accessToken\":\"%s\",\"tokenType\":\"Bearer\",\"accessTokenExpiresIn\":%d}",
                accessToken, jwtTokenProvider.getAccessTokenExpiry() / 1000
        ));
    }
}
