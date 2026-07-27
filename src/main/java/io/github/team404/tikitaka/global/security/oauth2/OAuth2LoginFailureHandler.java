package io.github.team404.tikitaka.global.security.oauth2;

import io.github.team404.tikitaka.global.exception.ErrorResponseWriter;
import io.github.team404.tikitaka.global.security.jwt.JwtErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

// 실패 원인(exception message)은 응답 바디에 포함하지 않고 서버 로그에만 남긴다.
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    private final ErrorResponseWriter errorResponseWriter;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                         HttpServletResponse response,
                                         AuthenticationException exception) throws IOException {
        log.warn("Google OAuth2 로그인 실패: {}", exception.getMessage());
        errorResponseWriter.write(response, JwtErrorCode.OAUTH2_AUTHENTICATION_FAILED);
    }
}
