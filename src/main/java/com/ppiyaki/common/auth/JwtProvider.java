package com.ppiyaki.common.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Objects;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtProvider {

    private final SecretKey secretKey;
    private final long accessTokenExpiry;
    private final long refreshTokenExpiry;

    public JwtProvider(final JwtProperties jwtProperties) {
        Objects.requireNonNull(jwtProperties, "jwtProperties must not be null");
        Objects.requireNonNull(jwtProperties.secret(), "jwt secret must not be null");
        this.secretKey = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiry = jwtProperties.accessTokenExpiry();
        this.refreshTokenExpiry = jwtProperties.refreshTokenExpiry();
    }

    public String createAccessToken(final Long userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        return createToken(userId, accessTokenExpiry);
    }

    public String createRefreshToken(final Long userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        return createToken(userId, refreshTokenExpiry);
    }

    public Long extractUserId(final String token) {
        Objects.requireNonNull(token, "token must not be null");
        final Claims claims = parseClaims(token);
        return claims.get("userId", Long.class);
    }

    public boolean isValid(final String token) {
        try {
            parseClaims(token);
            return true;
        } catch (final JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private String createToken(final Long userId, final long expiryMillis) {
        final Date now = new Date();
        final Date expiry = new Date(now.getTime() + expiryMillis);

        return Jwts.builder()
                .claim("userId", userId)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    private Claims parseClaims(final String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
