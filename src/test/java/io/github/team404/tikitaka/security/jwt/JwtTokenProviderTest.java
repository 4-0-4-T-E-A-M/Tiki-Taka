package io.github.team404.tikitaka.security.jwt;

import io.github.team404.tikitaka.global.security.jwt.JwtTokenProvider;
import io.github.team404.tikitaka.global.security.jwt.JwtValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private static final String VALID_SECRET =
            "dGlraXRha2EtdGVzdC1zZWNyZXQta2V5LWZvci1qd3QtMjU2Yml0cy1kZXY=";
    private static final long ACCESS_TOKEN_EXPIRY = 3_600_000L;
    private static final long REFRESH_TOKEN_EXPIRY = 604_800_000L;

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(VALID_SECRET, ACCESS_TOKEN_EXPIRY, REFRESH_TOKEN_EXPIRY);
    }

    @Test
    void Access_Token을_생성할_수_있다() {
        String token = jwtTokenProvider.generateAccessToken(1L);

        assertThat(token).isNotNull().isNotBlank();
    }

    @Test
    void Refresh_Token을_생성할_수_있다() {
        String token = jwtTokenProvider.generateRefreshToken(1L);

        assertThat(token).isNotNull().isNotBlank();
    }

    @Test
    void Access_Token에서_userId를_추출할_수_있다() {
        String token = jwtTokenProvider.generateAccessToken(42L);

        assertThat(jwtTokenProvider.getUserIdFromToken(token)).isEqualTo(42L);
    }

    @Test
    void Refresh_Token에서_userId를_추출할_수_있다() {
        String token = jwtTokenProvider.generateRefreshToken(42L);

        assertThat(jwtTokenProvider.getUserIdFromToken(token)).isEqualTo(42L);
    }

    @Test
    void Access_Token_검증에_성공한다() {
        String token = jwtTokenProvider.generateAccessToken(1L);

        jwtTokenProvider.validateAccessToken(token);
    }

    @Test
    void Refresh_Token_검증에_성공한다() {
        String token = jwtTokenProvider.generateRefreshToken(1L);

        jwtTokenProvider.validateRefreshToken(token);
    }

    @Test
    void Access_Token_검증_메서드는_Refresh_Token을_거부한다() {
        String refreshToken = jwtTokenProvider.generateRefreshToken(1L);

        assertThatThrownBy(() -> jwtTokenProvider.validateAccessToken(refreshToken))
                .isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("Access Token이 아닙니다.");
    }

    @Test
    void Refresh_Token_검증_메서드는_Access_Token을_거부한다() {
        String accessToken = jwtTokenProvider.generateAccessToken(1L);

        assertThatThrownBy(() -> jwtTokenProvider.validateRefreshToken(accessToken))
                .isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("Refresh Token이 아닙니다.");
    }

    @Test
    void 잘못된_서명의_토큰은_검증에_실패한다() {
        String differentSecret = "ZGlmZmVyZW50LXNlY3JldC1rZXktZm9yLXRlc3RpbmctcHVycG9zZXM=";
        JwtTokenProvider otherProvider =
                new JwtTokenProvider(differentSecret, ACCESS_TOKEN_EXPIRY, REFRESH_TOKEN_EXPIRY);
        String tokenFromOther = otherProvider.generateAccessToken(1L);

        assertThatThrownBy(() -> jwtTokenProvider.validateAccessToken(tokenFromOther))
                .isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("유효하지 않은 토큰입니다.");
    }

    @Test
    void 만료된_토큰은_검증에_실패한다() {
        JwtTokenProvider shortLivedProvider =
                new JwtTokenProvider(VALID_SECRET, 1L, REFRESH_TOKEN_EXPIRY);
        String token = shortLivedProvider.generateAccessToken(1L);

        assertThatThrownBy(() -> {
            Thread.sleep(5);
            shortLivedProvider.validateAccessToken(token);
        })
                .isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("만료된 토큰입니다.");
    }

    @Test
    void null_토큰은_검증에_실패한다() {
        assertThatThrownBy(() -> jwtTokenProvider.validateAccessToken(null))
                .isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("토큰이 비어 있습니다.");
    }

    @Test
    void 빈_토큰은_검증에_실패한다() {
        assertThatThrownBy(() -> jwtTokenProvider.validateAccessToken(""))
                .isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("토큰이 비어 있습니다.");
    }

    @Test
    void userId가_null이면_Access_Token을_생성하지_않는다() {
        assertThatThrownBy(() -> jwtTokenProvider.generateAccessToken(null))
                .isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("userId는 null일 수 없습니다.");
    }

    @Test
    void userId가_null이면_Refresh_Token을_생성하지_않는다() {
        assertThatThrownBy(() -> jwtTokenProvider.generateRefreshToken(null))
                .isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("userId는 null일 수 없습니다.");
    }
}
