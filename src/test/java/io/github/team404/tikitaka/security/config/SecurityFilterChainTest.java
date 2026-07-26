package io.github.team404.tikitaka.security.config;

import io.github.team404.tikitaka.TikitakaApplication;
import io.github.team404.tikitaka.global.security.jwt.JwtTokenProvider;
import io.github.team404.tikitaka.user.domain.OAuthProvider;
import io.github.team404.tikitaka.user.domain.User;
import io.github.team404.tikitaka.user.domain.UserRole;
import io.github.team404.tikitaka.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SecurityConfig가 실제로 구성하는 SecurityFilterChain의 행위(경로별 인증 요구, JWT 필터 적용,
 * 실패 응답 형식)를 코드 판독이 아닌 실제 요청/응답으로 검증한다.
 * TikitakaApplicationTests.contextLoads()는 Bean 생성만 증명하고 이 행위를 증명하지 않으므로 별도로 둔다.
 *
 * 보호 API가 아직 실제로 존재하지 않아, 이 테스트에서만 쓰는 컨트롤러를 추가 소스로 등록해
 * "인증 필요" 경로를 재현한다. 컴포넌트 스캔에 의해 다른 테스트 컨텍스트로 새지 않도록
 * @SpringBootTest(classes = ...)로 이 테스트 클래스에만 명시적으로 등록한다.
 */
@SpringBootTest(classes = {TikitakaApplication.class, SecurityFilterChainTest.ProtectedTestController.class})
@AutoConfigureMockMvc
class SecurityFilterChainTest {

    private Long userId;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        userId = userRepository.save(
                new User("test@example.com", "테스트", OAuthProvider.GOOGLE, "provider-id"))
                .getUserId();
    }

    @Test
    void 공개_경로_api_auth_refresh는_토큰_없이도_필터에_막히지_않고_컨트롤러까지_도달한다() throws Exception {
        // permitAll이 아니라면 JwtAuthenticationEntryPoint의 "인증이 필요합니다." 메시지가 나온다.
        // AuthController 자체 메시지가 나온다는 것은 요청이 Security 계층을 통과해 컨트롤러까지 도달했다는 뜻이다.
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Refresh Token이 없습니다."));
    }

    @Test
    void 보호_경로는_토큰_없이_접근하면_401과_공통_인증실패_메시지를_반환한다() throws Exception {
        mockMvc.perform(get("/test/protected"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."));
    }

    @Test
    void 보호_경로는_정상_Access_Token으로_접근하면_200을_반환한다() throws Exception {
        String accessToken = jwtTokenProvider.generateAccessToken(userId, UserRole.USER);

        mockMvc.perform(get("/test/protected").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(content().string("ok:" + userId));
    }

    @Test
    void 보호_경로에_Refresh_Token을_Access_Token으로_사용하면_401이다() throws Exception {
        String refreshToken = jwtTokenProvider.generateRefreshToken(1L);

        mockMvc.perform(get("/test/protected").header("Authorization", "Bearer " + refreshToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Access Token이 아닙니다."));
    }

    @Test
    void 보호_경로에_변조된_토큰으로_접근하면_401이고_필터_고유_메시지를_반환한다() throws Exception {
        String accessToken = jwtTokenProvider.generateAccessToken(userId, UserRole.USER);

        mockMvc.perform(get("/test/protected").header("Authorization", "Bearer " + accessToken + "tampered"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("유효하지 않은 토큰입니다."));
    }

    @Test
    void oauth2_경로는_토큰_없이도_리다이렉트로_처리되어_인증_실패_JSON을_반환하지_않는다() throws Exception {
        // permitAll이므로 401 JSON이 아니라 302 리다이렉트(구글 인가 엔드포인트로)가 나와야 한다.
        mockMvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void ADMIN은_관리자_경로에_접근할_수_있다() throws Exception {
        User user = userRepository.findById(userId).orElseThrow();
        user.updateRole(UserRole.ADMIN);
        userRepository.saveAndFlush(user);
        org.assertj.core.api.Assertions.assertThat(userRepository.findById(userId).orElseThrow().getRole())
                .isEqualTo(UserRole.ADMIN);

        mockMvc.perform(get("/api/admin/test")
                        .header("Authorization", "Bearer " + jwtTokenProvider.generateAccessToken(userId, UserRole.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(content().string("admin-ok"));
    }

    @Test
    void USER는_관리자_경로에_접근하면_403을_반환한다() throws Exception {
        mockMvc.perform(get("/api/admin/test")
                        .header("Authorization", "Bearer " + jwtTokenProvider.generateAccessToken(userId, UserRole.USER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("접근 권한이 없습니다."));
    }

    @Test
    void 미인증_사용자는_관리자_경로에_접근하면_401을_반환한다() throws Exception {
        mockMvc.perform(get("/api/admin/test"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."));
    }

    @RestController
    static class ProtectedTestController {
        @GetMapping("/test/protected")
        public String protectedEndpoint(org.springframework.security.core.Authentication authentication) {
            io.github.team404.tikitaka.global.security.principal.CustomUserPrincipal principal =
                    (io.github.team404.tikitaka.global.security.principal.CustomUserPrincipal) authentication.getPrincipal();
            return "ok:" + principal.getUserId();
        }

        @GetMapping("/api/admin/test")
        public String adminEndpoint() {
            return "admin-ok";
        }
    }
}
