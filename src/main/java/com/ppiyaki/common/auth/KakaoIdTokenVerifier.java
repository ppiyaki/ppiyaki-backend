package com.ppiyaki.common.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ppiyaki.common.exception.BusinessException;
import com.ppiyaki.common.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Header;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Locator;
import io.jsonwebtoken.security.SignatureException;
import java.math.BigInteger;
import java.security.Key;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class KakaoIdTokenVerifier {

    private static final String RSA_KEY_TYPE = "RSA";
    private static final String NICKNAME_CLAIM = "nickname";

    private final KakaoOidcProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final Map<String, PublicKey> keyCache;

    public KakaoIdTokenVerifier(final KakaoOidcProperties properties,
            final RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
        this.objectMapper = new ObjectMapper();
        this.keyCache = new ConcurrentHashMap<>();
    }

    public KakaoIdTokenPayload verify(final String idToken) {
        if (idToken == null || idToken.isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
        }

        try {
            final Locator<Key> keyLocator = this::resolveKey;
            final Jws<Claims> jws = Jwts.parser()
                    .keyLocator(keyLocator)
                    .build()
                    .parseSignedClaims(idToken);

            final Claims claims = jws.getPayload();
            validateStandardClaims(claims);

            final String sub = claims.getSubject();
            final String nickname = claims.get(NICKNAME_CLAIM, String.class);

            return new KakaoIdTokenPayload(sub, nickname);
        } catch (final JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
        }
    }

    private void validateStandardClaims(final Claims claims) {
        if (!properties.issuer().equals(claims.getIssuer())) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
        }
        if (claims.getAudience() == null || !claims.getAudience().contains(properties.audience())) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
        }
        final String sub = claims.getSubject();
        if (sub == null || sub.isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
        }
    }

    private Key resolveKey(final Header header) {
        if (!(header instanceof final JwsHeader jwsHeader)) {
            throw new SignatureException("not a JWS header");
        }
        final String kid = jwsHeader.getKeyId();
        if (kid == null || kid.isBlank()) {
            throw new SignatureException("missing kid");
        }

        PublicKey cached = keyCache.get(kid);
        if (cached == null) {
            refreshJwks();
            cached = keyCache.get(kid);
        }
        if (cached == null) {
            throw new SignatureException("unknown kid");
        }
        return cached;
    }

    private synchronized void refreshJwks() {
        try {
            final String jwksJson = restClient.get()
                    .uri(properties.jwksUri())
                    .retrieve()
                    .body(String.class);

            if (jwksJson == null) {
                throw new SignatureException("empty jwks response");
            }

            final JsonNode root = objectMapper.readTree(jwksJson);
            final JsonNode keys = root.path("keys");
            final Map<String, PublicKey> refreshedCache = new HashMap<>();

            for (final JsonNode jwk : keys) {
                final String kty = jwk.path("kty").asText();
                if (!RSA_KEY_TYPE.equals(kty)) {
                    continue;
                }
                final String kid = jwk.path("kid").asText();
                final String modulusBase64 = jwk.path("n").asText();
                final String exponentBase64 = jwk.path("e").asText();
                if (kid.isBlank() || modulusBase64.isBlank() || exponentBase64.isBlank()) {
                    continue;
                }
                final BigInteger modulus = new BigInteger(1,
                        Base64.getUrlDecoder().decode(modulusBase64));
                final BigInteger exponent = new BigInteger(1,
                        Base64.getUrlDecoder().decode(exponentBase64));
                final RSAPublicKeySpec keySpec = new RSAPublicKeySpec(modulus, exponent);
                final PublicKey publicKey = KeyFactory.getInstance(RSA_KEY_TYPE).generatePublic(keySpec);
                refreshedCache.put(kid, publicKey);
            }

            keyCache.clear();
            keyCache.putAll(refreshedCache);
        } catch (final SignatureException e) {
            throw e;
        } catch (final Exception e) {
            throw new SignatureException("jwks fetch failed");
        }
    }

    public record KakaoIdTokenPayload(
            String sub,
            String nickname
    ) {
    }
}
