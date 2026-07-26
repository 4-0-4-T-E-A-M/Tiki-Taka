package io.github.team404.tikitaka.global.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Security 필터/핸들러(EntryPoint, AccessDeniedHandler, JWT 필터 등)처럼
 * DispatcherServlet 이전에 실행되어 GlobalExceptionHandler가 닿지 못하는 지점에서
 * 동일한 {@link ErrorResponse} 형식으로 응답을 직접 써야 할 때 사용한다.
 */
@Component
@RequiredArgsConstructor
public class ErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ErrorResponse.from(errorCode));
    }
}
