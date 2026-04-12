package com.ppiyaki.common.auth;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.ppiyaki.common.auth.KakaoIdTokenVerifier.KakaoIdTokenPayload;
import com.ppiyaki.common.exception.BusinessException;
import com.ppiyaki.common.exception.ErrorCode;
import io.jsonwebtoken.Jwts;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class KakaoIdTokenVerifierTest {

    private static final int WIREMOCK_PORT = 19890;
    private static final String JWKS_PATH = "/.well-known/jwks.json";
    private static final String TEST_ISSUER = "https://kauth.kakao.com";
    private static final String TEST_AUDIENCE = "test-app-key";
    private static final String TEST_KID = "test-kid-1";
    private static final long HOUR_MILLIS = 3_600_000L;

    private static WireMockServer wireMockServer;
    private static KeyPair trustedKeyPair;
    private static KeyPair attackerKeyPair;

    private KakaoIdTokenVerifier verifier;

    @BeforeAll
    static void startAll() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().port(WIREMOCK_PORT));
        wireMockServer.start();
        trustedKeyPair = Jwts.SIG.RS256.keyPair().build();
        attackerKeyPair = Jwts.SIG.RS256.keyPair().build();
    }

    @AfterAll
    static void stopAll() {
        wireMockServer.stop();
    }

    @BeforeEach
    void setUp() {
        wireMockServer.resetAll();
        stubJwks(trustedKeyPair, TEST_KID);

        final KakaoOidcProperties properties = new KakaoOidcProperties(
                TEST_ISSUER,
                "http://localhost:" + WIREMOCK_PORT + JWKS_PATH,
                TEST_AUDIENCE
        );
        verifier = new KakaoIdTokenVerifier(properties, RestClient.builder());
    }

    @Test
    @DisplayName("정상 ID Token 검증 시 sub와 nickname을 반환한다")
    void verify_validToken_returnsPayload() {
        // given
        final String idToken = buildToken(trustedKeyPair, TEST_KID, TEST_ISSUER, TEST_AUDIENCE,
                "12345", "테스트닉네임", new Date(), oneHourLater());

        // when
        final KakaoIdTokenPayload payload = verifier.verify(idToken);

        // then
        assertThat(payload.sub()).isEqualTo("12345");
        assertThat(payload.nickname()).isEqualTo("테스트닉네임");
    }

    @Test
    @DisplayName("두 번째 검증 호출 시 JWKS 엔드포인트를 재호출하지 않는다")
    void verify_jwksCachedAfterFirstCall() {
        // given
        final String firstToken = buildToken(trustedKeyPair, TEST_KID, TEST_ISSUER, TEST_AUDIENCE,
                "11111", "첫번째", new Date(), oneHourLater());
        final String secondToken = buildToken(trustedKeyPair, TEST_KID, TEST_ISSUER, TEST_AUDIENCE,
                "22222", "두번째", new Date(), oneHourLater());

        // when
        verifier.verify(firstToken);
        verifier.verify(secondToken);

        // then - JWKS 엔드포인트는 최초 1회만 호출되어야 한다
        wireMockServer.verify(1, getRequestedFor(urlPathEqualTo(JWKS_PATH)));
    }

    @Test
    @DisplayName("공격자 키로 서명된 토큰은 401 AUTH_INVALID_TOKEN으로 거부된다")
    void verify_signedWithDifferentKey_throws401() {
        // given - JWKS에는 trustedKeyPair의 공개키만 등록. 토큰은 attackerKeyPair 개인키로 서명
        final String forgedToken = buildToken(attackerKeyPair, TEST_KID, TEST_ISSUER, TEST_AUDIENCE,
                "12345", "공격자닉네임", new Date(), oneHourLater());

        // when & then
        assertInvalidToken(() -> verifier.verify(forgedToken));
    }

    @Test
    @DisplayName("JWKS에 존재하지 않는 kid를 가진 토큰은 401로 거부된다")
    void verify_unknownKid_throws401() {
        // given - JWKS에 등록된 kid는 TEST_KID 뿐인데 토큰 헤더는 다른 kid 사용
        final String tokenWithUnknownKid = buildToken(trustedKeyPair, "unknown-kid",
                TEST_ISSUER, TEST_AUDIENCE, "12345", "닉네임", new Date(), oneHourLater());

        // when & then
        assertInvalidToken(() -> verifier.verify(tokenWithUnknownKid));
    }

    @Test
    @DisplayName("kid 헤더가 없는 토큰은 401로 거부된다")
    void verify_missingKidHeader_throws401() {
        // given - kid 헤더 없이 서명
        final Date now = new Date();
        final String tokenWithoutKid = Jwts.builder()
                .issuer(TEST_ISSUER)
                .audience().add(TEST_AUDIENCE).and()
                .subject("12345")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + HOUR_MILLIS))
                .signWith(trustedKeyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();

        // when & then
        assertInvalidToken(() -> verifier.verify(tokenWithoutKid));
    }

    @Test
    @DisplayName("완전히 손상된 문자열은 401로 거부된다")
    void verify_malformedToken_throws401() {
        assertInvalidToken(() -> verifier.verify("garbage"));
        assertInvalidToken(() -> verifier.verify("not.a.valid.jwt.structure"));
        assertInvalidToken(() -> verifier.verify(""));
    }

    @Test
    @DisplayName("만료된 토큰(exp 과거)은 401로 거부된다")
    void verify_expiredToken_throws401() {
        // given - exp가 10초 전
        final Date longAgo = new Date(System.currentTimeMillis() - HOUR_MILLIS);
        final Date tenSecondsAgo = new Date(System.currentTimeMillis() - 10_000L);
        final String expiredToken = buildToken(trustedKeyPair, TEST_KID, TEST_ISSUER, TEST_AUDIENCE,
                "12345", "만료유저", longAgo, tenSecondsAgo);

        // when & then
        assertInvalidToken(() -> verifier.verify(expiredToken));
    }

    @Test
    @DisplayName("iss가 카카오 인증 서버가 아닌 토큰은 401로 거부된다")
    void verify_wrongIssuer_throws401() {
        // given - 공격자가 카카오 키 탈취에 성공했다고 가정해도, 다른 발급자 문자열로는 통과 불가
        final String wrongIssuerToken = buildToken(trustedKeyPair, TEST_KID, "https://evil.example.com",
                TEST_AUDIENCE, "12345", "닉네임", new Date(), oneHourLater());

        // when & then
        assertInvalidToken(() -> verifier.verify(wrongIssuerToken));
    }

    @Test
    @DisplayName("aud가 우리 앱 키가 아닌 토큰은 401로 거부된다 (token substitution 공격 차단)")
    void verify_wrongAudience_throws401() {
        // given - 동일한 카카오 JWKS로 서명되었으나 다른 카카오 앱 키를 대상으로 발급된 토큰
        final String wrongAudienceToken = buildToken(trustedKeyPair, TEST_KID, TEST_ISSUER,
                "other-kakao-app-key", "12345", "닉네임", new Date(), oneHourLater());

        // when & then
        assertInvalidToken(() -> verifier.verify(wrongAudienceToken));
    }

    @Test
    @DisplayName("sub 클레임이 null인 토큰은 401로 거부된다")
    void verify_nullSub_throws401() {
        // given - subject 호출 없이 서명 (sub 클레임 부재)
        final Date now = new Date();
        final String tokenWithoutSub = Jwts.builder()
                .header().keyId(TEST_KID).and()
                .issuer(TEST_ISSUER)
                .audience().add(TEST_AUDIENCE).and()
                .issuedAt(now)
                .expiration(new Date(now.getTime() + HOUR_MILLIS))
                .signWith(trustedKeyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();

        // when & then
        assertInvalidToken(() -> verifier.verify(tokenWithoutSub));
    }

    @Test
    @DisplayName("sub 클레임이 공백 문자열인 토큰은 401로 거부된다")
    void verify_blankSub_throws401() {
        // given
        final String tokenWithBlankSub = buildToken(trustedKeyPair, TEST_KID, TEST_ISSUER,
                TEST_AUDIENCE, "   ", "닉네임", new Date(), oneHourLater());

        // when & then
        assertInvalidToken(() -> verifier.verify(tokenWithBlankSub));
    }

    @Test
    @DisplayName("JWKS 엔드포인트가 500을 반환하면 토큰 검증이 401로 실패한다")
    void verify_jwksServerError_throws401() {
        // given - 기본 stub 제거 후 500 응답 stub 설정
        wireMockServer.resetAll();
        wireMockServer.stubFor(get(urlPathEqualTo(JWKS_PATH))
                .willReturn(aResponse().withStatus(500)));

        final String validToken = buildToken(trustedKeyPair, TEST_KID, TEST_ISSUER, TEST_AUDIENCE,
                "12345", "닉네임", new Date(), oneHourLater());

        // when & then
        assertInvalidToken(() -> verifier.verify(validToken));
    }

    private static void assertInvalidToken(final ThrowingRunnable runnable) {
        assertThatThrownBy(runnable::run)
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_INVALID_TOKEN);
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static Date oneHourLater() {
        return new Date(System.currentTimeMillis() + HOUR_MILLIS);
    }

    private static String buildToken(
            final KeyPair keyPair,
            final String kid,
            final String issuer,
            final String audience,
            final String sub,
            final String nickname,
            final Date issuedAt,
            final Date expiry
    ) {
        return Jwts.builder()
                .header().keyId(kid).and()
                .issuer(issuer)
                .audience().add(audience).and()
                .subject(sub)
                .claim("nickname", nickname)
                .issuedAt(issuedAt)
                .expiration(expiry)
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    private static void stubJwks(final KeyPair keyPair, final String kid) {
        final String jwksJson = buildJwksJson((RSAPublicKey) keyPair.getPublic(), kid);
        wireMockServer.stubFor(get(urlPathEqualTo(JWKS_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jwksJson)));
    }

    private static String buildJwksJson(final RSAPublicKey publicKey, final String kid) {
        final String modulusBase64 = base64UrlEncode(publicKey.getModulus());
        final String exponentBase64 = base64UrlEncode(publicKey.getPublicExponent());
        return """
                {
                    "keys": [
                        {
                            "kty": "RSA",
                            "kid": "%s",
                            "alg": "RS256",
                            "use": "sig",
                            "n": "%s",
                            "e": "%s"
                        }
                    ]
                }
                """.formatted(kid, modulusBase64, exponentBase64);
    }

    private static String base64UrlEncode(final BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
