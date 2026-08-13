package io.github.team404.tikitaka.user.controller;

import io.github.team404.tikitaka.global.exception.BusinessException;
import io.github.team404.tikitaka.global.response.BaseResponse;
import io.github.team404.tikitaka.global.security.jwt.JwtErrorCode;
import io.github.team404.tikitaka.global.security.jwt.JwtTokenProvider;
import io.github.team404.tikitaka.global.security.jwt.JwtValidationException;
import io.github.team404.tikitaka.user.domain.User;
import io.github.team404.tikitaka.user.dto.response.AccessTokenResponse;
import io.github.team404.tikitaka.user.exception.UserErrorCode;
import io.github.team404.tikitaka.user.service.UserSignupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Refresh Token은 OAuth2LoginSuccessHandler가 심어둔 HttpOnly 쿠키로 전달되므로
// 요청 바디가 아닌 쿠키에서 읽는다. 오류 응답은 GlobalExceptionHandler가 공통 포맷으로 변환한다.
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserSignupService userSignupService;

    @PostMapping("/refresh")
    public ResponseEntity<BaseResponse<AccessTokenResponse>> refresh(
            @CookieValue(name = "refreshToken", required = false) String refreshToken) {
        if (refreshToken == null) {
            throw new JwtValidationException(JwtErrorCode.REFRESH_TOKEN_NOT_FOUND);
        }

        jwtTokenProvider.validateRefreshToken(refreshToken);

        Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
        User user = userSignupService.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        String newAccessToken = jwtTokenProvider.generateAccessToken(userId, user.getRole());
        AccessTokenResponse response = AccessTokenResponse.of(
                newAccessToken, jwtTokenProvider.getAccessTokenExpiry());
        return ResponseEntity.ok(
                BaseResponse.success("액세스 토큰 재발급에 성공했습니다.", response)
        );
    }
}
