package io.github.team404.tikitaka.security.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.team404.tikitaka.global.exception.ErrorResponseWriter;
import io.github.team404.tikitaka.global.security.jwt.JwtAuthenticationFilter;
import io.github.team404.tikitaka.global.security.jwt.JwtErrorCode;
import io.github.team404.tikitaka.global.security.jwt.JwtTokenProvider;
import io.github.team404.tikitaka.global.security.jwt.JwtValidationException;
import io.github.team404.tikitaka.global.security.principal.CustomUserPrincipal;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * JwtAuthenticationFilter 단독(행위) 테스트.
 * JwtTokenProvider는 Mock으로 대체하여 필터 자체의 분기 처리만 검증한다.
 * SecurityFilterChain을 포함한 경로 접근 제어 검증은 SecurityFilterChainTest에서 다룬다.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtTokenProvider, new ErrorResponseWriter(new ObjectMapper().findAndRegisterModules()));
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 정상_Access_Token이면_SecurityContext에_인증객체를_저장하고_다음_필터로_넘어간다() throws Exception {
        given(jwtTokenProvider.getUserIdFromToken("valid-access-token")).willReturn(42L);

        MockHttpServletRequest request = requestWithBearer("valid-access-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain chain = new RecordingFilterChain();

        filter.doFilter(request, response, chain);

        verify(jwtTokenProvider).validateAccessToken("valid-access-token");
        assertThat(chain.called.get()).isTrue();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isInstanceOf(CustomUserPrincipal.class);
        assertThat(((CustomUserPrincipal) authentication.getPrincipal()).getUserId()).isEqualTo(42L);
    }

    @Test
    void Authorization_헤더가_없으면_인증객체를_저장하지_않고_체인을_통과시킨다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain chain = new RecordingFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.called.get()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void Bearer_접두사가_아니면_토큰이_없는_것으로_간주하고_체인을_통과시킨다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain chain = new RecordingFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.called.get()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void 빈_Bearer_토큰이면_필터가_직접_401을_응답하고_체인을_호출하지_않는다() throws Exception {
        willThrow(new JwtValidationException(JwtErrorCode.EMPTY_TOKEN))
                .given(jwtTokenProvider).validateAccessToken("");

        MockHttpServletRequest request = requestWithBearer("");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain chain = new RecordingFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.called.get()).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("토큰이 비어 있습니다.");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void Refresh_Token을_Access_Token_대신_사용하면_401이고_체인을_호출하지_않는다() throws Exception {
        willThrow(new JwtValidationException(JwtErrorCode.NOT_ACCESS_TOKEN))
                .given(jwtTokenProvider).validateAccessToken("refresh-token-value");

        MockHttpServletRequest request = requestWithBearer("refresh-token-value");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain chain = new RecordingFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.called.get()).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Access Token이 아닙니다.");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void 만료된_토큰이면_401이고_체인을_호출하지_않는다() throws Exception {
        willThrow(new JwtValidationException(JwtErrorCode.EXPIRED_TOKEN))
                .given(jwtTokenProvider).validateAccessToken("expired-token");

        MockHttpServletRequest request = requestWithBearer("expired-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain chain = new RecordingFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.called.get()).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("만료된 토큰입니다.");
    }

    @Test
    void 변조된_토큰이면_401이고_체인을_호출하지_않는다() throws Exception {
        willThrow(new JwtValidationException(JwtErrorCode.INVALID_TOKEN))
                .given(jwtTokenProvider).validateAccessToken("tampered-token");

        MockHttpServletRequest request = requestWithBearer("tampered-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain chain = new RecordingFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.called.get()).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("유효하지 않은 토큰입니다.");
    }

    /**
     * 코드에는 "이미 인증된 요청인지" 확인하는 분기가 없다. 이 테스트는 결함을 주장하는 것이
     * 아니라 실제 동작(기존 인증이 있어도 새 토큰의 인증으로 덮어씀)을 있는 그대로 기록한다.
     * OncePerRequestFilter로 요청당 1회만 실행되므로 실제 위험은 낮지만, 향후 필터 재사용
     * 시나리오가 생기면 재검토가 필요하다는 근거 자료로 남긴다.
     */
    @Test
    void 이미_인증된_SecurityContext가_있어도_새로운_토큰의_인증으로_덮어쓴다() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new CustomUserPrincipal(1L), null, Collections.emptyList()));
        given(jwtTokenProvider.getUserIdFromToken("valid-access-token")).willReturn(99L);

        MockHttpServletRequest request = requestWithBearer("valid-access-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain chain = new RecordingFilterChain();

        filter.doFilter(request, response, chain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(((CustomUserPrincipal) authentication.getPrincipal()).getUserId()).isEqualTo(99L);
    }

    @Test
    void 예외_응답_후에는_다음_필터체인을_다시_호출하지_않는다() throws Exception {
        willThrow(new JwtValidationException(JwtErrorCode.INVALID_TOKEN))
                .given(jwtTokenProvider).validateAccessToken("bad-token");

        MockHttpServletRequest request = requestWithBearer("bad-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain chain = new RecordingFilterChain();

        filter.doFilter(request, response, chain);

        verify(jwtTokenProvider, never()).getUserIdFromToken("bad-token");
        assertThat(chain.called.get()).isFalse();
    }

    private MockHttpServletRequest requestWithBearer(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private static class RecordingFilterChain implements FilterChain {
        private final AtomicBoolean called = new AtomicBoolean(false);

        @Override
        public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {
            called.set(true);
        }
    }
}
