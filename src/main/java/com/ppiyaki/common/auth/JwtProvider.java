package com.ppiyaki.common.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class JwtProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtProvider.class);

    private final SecretKey secretKey;
    private final long accessTokenExpiry;
    private final long refreshTokenExpiry;

    public JwtProvider(final JwtProperties jwtProperties) {
        this.secretKey = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiry = jwtProperties.accessTokenExpiry();
        this.refreshTokenExpiry = jwtProperties.refreshTokenExpiry();
    }

    @Deprecated
    public String createAccessToken(final Long userId) {
        return createToken(userId, null, accessTokenExpiry);
    }

    public String createAccessToken(final Long userId, final String role) {
        return createToken(userId, role, accessTokenExpiry);
    }

    public String createRefreshToken(final Long userId) {
        return createToken(userId, null, refreshTokenExpiry);
    }

    public Long extractUserId(final String token) {
        final Claims claims = parseClaims(token);
        return claims.get("userId", Long.class);
    }

    public String extractRole(final String token) {
        final Claims claims = parseClaims(token);
        return claims.get("role", String.class);
    }

    public boolean isValid(final String token) {
        try {
            parseClaims(token);
            return true;
        } catch (final JwtException | IllegalArgumentException e) {
            // 토큰 값은 절대 남기지 않고 실패 사유(예외 클래스)만 기록 — 만료/서명불일치/형식오류 판별용.
            log.warn("JWT 검증 실패: {}", e.getClass().getSimpleName());
            return false;
        }
    }

    private String createToken(final Long userId, final String role, final long expiryMillis) {
        final Date now = new Date();
        final Date expiry = new Date(now.getTime() + expiryMillis);

        final var builder = Jwts.builder()
                .claim("userId", userId)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey);

        if (role != null) {
            builder.claim("role", role);
        }

        return builder.compact();
    }

    private Claims parseClaims(final String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
