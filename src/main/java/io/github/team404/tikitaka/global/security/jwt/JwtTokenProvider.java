package io.github.team404.tikitaka.global.security.jwt;

import io.github.team404.tikitaka.global.exception.JwtValidationException;
import io.github.team404.tikitaka.user.domain.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private static final String TOKEN_TYPE_CLAIM = "type";
    private static final String ROLE_CLAIM = "role";
    private static final String ACCESS = "ACCESS";
    private static final String REFRESH = "REFRESH";

    private final SecretKey signingKey;
    private final long accessTokenExpiry;
    private final long refreshTokenExpiry;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secretKey,
            @Value("${jwt.access-token-expiry}") long accessTokenExpiry,
            @Value("${jwt.refresh-token-expiry}") long refreshTokenExpiry) {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretKey));
        this.accessTokenExpiry = accessTokenExpiry;
        this.refreshTokenExpiry = refreshTokenExpiry;
    }

    public String generateAccessToken(Long userId, UserRole role) {
        if (userId == null) {
            throw new JwtValidationException("userId는 null일 수 없습니다.");
        }
        if (role == null) {
            throw new JwtValidationException("role은 null일 수 없습니다.");
        }
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(TOKEN_TYPE_CLAIM, ACCESS)
                .claim(ROLE_CLAIM, role.name())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTokenExpiry))
                .signWith(signingKey)
                .compact();
    }

    public String generateRefreshToken(Long userId) {
        if (userId == null) {
            throw new JwtValidationException("userId는 null일 수 없습니다.");
        }
        return buildToken(userId, REFRESH, refreshTokenExpiry);
    }

    public void validateAccessToken(String token) {
        validateNotBlank(token);
        Claims claims = parseClaims(token);
        if (!ACCESS.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
            throw new JwtValidationException("Access Token이 아닙니다.");
        }
    }

    public void validateRefreshToken(String token) {
        validateNotBlank(token);
        Claims claims = parseClaims(token);
        if (!REFRESH.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
            throw new JwtValidationException("Refresh Token이 아닙니다.");
        }
    }

    public Long getUserIdFromToken(String token) {
        String subject = parseClaims(token).getSubject();
        try {
            return Long.parseLong(subject);
        } catch (NumberFormatException e) {
            throw new JwtValidationException("유효하지 않은 사용자 ID입니다.");
        }
    }

    public String getRoleFromToken(String token) {
        return parseClaims(token).get(ROLE_CLAIM, String.class);
    }

    public long getAccessTokenExpiry() {
        return accessTokenExpiry;
    }

    public long getRefreshTokenExpiry() {
        return refreshTokenExpiry;
    }

    private void validateNotBlank(String token) {
        if (token == null || token.isBlank()) {
            throw new JwtValidationException("토큰이 비어 있습니다.");
        }
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new JwtValidationException("만료된 토큰입니다.");
        } catch (JwtException e) {
            throw new JwtValidationException("유효하지 않은 토큰입니다.");
        }
    }

    private String buildToken(Long userId, String type, long expiry) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(TOKEN_TYPE_CLAIM, type)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiry))
                .signWith(signingKey)
                .compact();
    }
}
