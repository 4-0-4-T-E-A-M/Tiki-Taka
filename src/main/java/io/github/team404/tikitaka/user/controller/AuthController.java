package io.github.team404.tikitaka.user.controller;

import io.github.team404.tikitaka.global.exception.JwtValidationException;
import io.github.team404.tikitaka.global.security.jwt.JwtTokenProvider;
import io.github.team404.tikitaka.user.dto.response.AccessTokenResponse;
import io.github.team404.tikitaka.user.service.UserSignupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

// Refresh Token은 OAuth2LoginSuccessHandler가 심어둔 HttpOnly 쿠키로 전달되므로
// 요청 바디가 아닌 쿠키에서 읽는다. 응답은 임시 최소 형식(Map)이며,
// 공통 에러 응답 포맷 통합은 Issue #21 범위.
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserSignupService userSignupService;

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(
            @CookieValue(name = "refreshToken", required = false) String refreshToken) {
        if (refreshToken == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Refresh Token이 없습니다."));
        }

        try {
            jwtTokenProvider.validateRefreshToken(refreshToken);
        } catch (JwtValidationException e) {
            return ResponseEntity.status(401).body(Map.of("message", e.getMessage()));
        }

        Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
        if (!userSignupService.existsById(userId)) {
            return ResponseEntity.status(401).body(Map.of("message", "존재하지 않는 사용자입니다."));
        }

        String newAccessToken = jwtTokenProvider.generateAccessToken(userId);
        return ResponseEntity.ok(
                AccessTokenResponse.of(newAccessToken, jwtTokenProvider.getAccessTokenExpiry())
        );
    }
}
