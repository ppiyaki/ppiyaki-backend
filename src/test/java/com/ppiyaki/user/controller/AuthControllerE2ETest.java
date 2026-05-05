package com.ppiyaki.user.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.jsonwebtoken.Jwts;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "kakao.oidc.issuer=https://kauth.kakao.com",
        "kakao.oidc.jwks-uri=http://localhost:19876/.well-known/jwks.json",
        "kakao.oidc.audience=test-app-key"
})
class AuthControllerE2ETest {

    private static final int WIREMOCK_PORT = 19876;
    private static final String TEST_KID = "test-kid-1";
    private static final String TEST_ISSUER = "https://kauth.kakao.com";
    private static final String TEST_AUDIENCE = "test-app-key";
    private static final long TOKEN_EXPIRY_MILLIS = 3_600_000L;

    private static WireMockServer wireMockServer;
    private static KeyPair keyPair;
    private static KeyPair attackerKeyPair;
    private static String jwksJson;

    @LocalServerPort
    private int port;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().port(WIREMOCK_PORT));
        wireMockServer.start();
        keyPair = Jwts.SIG.RS256.keyPair().build();
        attackerKeyPair = Jwts.SIG.RS256.keyPair().build();
        jwksJson = buildJwksJson((RSAPublicKey) keyPair.getPublic(), TEST_KID);
    }

    @AfterAll
    static void stopWireMock() {
        wireMockServer.stop();
    }

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        wireMockServer.resetAll();
        stubJwksEndpoint();
    }

    @Test
    @DisplayName("신규 유저 카카오 OIDC 로그인 시 JWT 발급 및 isOnboarded=true")
    void kakaoLogin_newUser() {
        // given
        final String idToken = buildIdToken("12345", "테스트닉네임");

        // when & then
        RestAssured.given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "idToken": "%s"
                        }
                        """.formatted(idToken))
                .when()
                .post("/api/v1/auth/kakao")
                .then()
                .statusCode(200)
                .body("accessToken", notNullValue())
                .body("refreshToken", notNullValue())
                .body("isOnboarded", is(true));
    }

    @Test
    @DisplayName("기존 유저 재로그인 시 JWT 재발급")
    void kakaoLogin_existingUser() {
        // given - 첫 로그인으로 유저 생성
        final String firstIdToken = buildIdToken("67890", "기존유저");

        RestAssured.given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "idToken": "%s"
                        }
                        """.formatted(firstIdToken))
                .when()
                .post("/api/v1/auth/kakao")
                .then()
                .statusCode(200);

        // when - 재로그인 (동일 sub)
        final String secondIdToken = buildIdToken("67890", "기존유저");

        RestAssured.given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "idToken": "%s"
                        }
                        """.formatted(secondIdToken))
                .when()
                .post("/api/v1/auth/kakao")
                .then()
                .statusCode(200)
                .body("accessToken", notNullValue())
                .body("refreshToken", notNullValue());
    }

    @Test
    @DisplayName("유효한 refresh token으로 새 토큰 쌍 발급")
    void refresh_validToken() {
        // given
        final String idToken = buildIdToken("99999", "리프레시유저");

        final String refreshToken = RestAssured.given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "idToken": "%s"
                        }
                        """.formatted(idToken))
                .when()
                .post("/api/v1/auth/kakao")
                .then()
                .extract()
                .path("refreshToken");

        // when & then
        RestAssured.given()
                .contentType(ContentType.JSON)
                .body("{\"refreshToken\": \"" + refreshToken + "\"}")
                .when()
                .post("/api/v1/auth/refresh")
                .then()
                .statusCode(200)
                .body("accessToken", notNullValue())
                .body("refreshToken", notNullValue());
    }

    @Test
    @DisplayName("인증된 유저의 정보를 조회한다")
    void me_authenticated() {
        // given
        final String idToken = buildIdToken("77777", "내정보유저");

        final String accessToken = RestAssured.given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "idToken": "%s"
                        }
                        """.formatted(idToken))
                .when()
                .post("/api/v1/auth/kakao")
                .then()
                .extract()
                .path("accessToken");

        // when & then
        RestAssured.given()
                .header("Authorization", "Bearer " + accessToken)
                .when()
                .get("/api/v1/users/me")
                .then()
                .statusCode(200)
                .body("nickname", is("내정보유저"))
                .body("isOnboarded", is(true));
    }

    @Test
    @DisplayName("인증 없이 호출하면 401 + AUTH_001 ErrorResponse")
    void me_unauthorized() {
        RestAssured.given()
                .when()
                .get("/api/v1/users/me")
                .then()
                .statusCode(401)
                .body("success", is(false))
                .body("error.code", is("AUTH_001"))
                .body("error.status", is(401));
    }

    @Test
    @DisplayName("잘못된 JSON 바디는 400 + MALFORMED_REQUEST(COMMON_002)로 거부된다")
    void kakaoLogin_malformedBody_returns400() {
        RestAssured.given()
                .contentType(ContentType.JSON)
                .body("{not valid json")
                .when()
                .post("/api/v1/auth/kakao")
                .then()
                .statusCode(400)
                .body("success", is(false))
                .body("error.code", is("COMMON_002"));
    }

    @Test
    @DisplayName("만료된 카카오 ID Token으로 로그인 시도하면 401")
    void kakaoLogin_expiredToken_returns401() {
        // given - exp가 10초 전
        final Date longAgo = new Date(System.currentTimeMillis() - TOKEN_EXPIRY_MILLIS);
        final Date tenSecondsAgo = new Date(System.currentTimeMillis() - 10_000L);
        final String expiredIdToken = buildIdToken(keyPair, TEST_KID, TEST_ISSUER, TEST_AUDIENCE,
                "33333", "만료유저", longAgo, tenSecondsAgo);

        // when & then
        RestAssured.given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "idToken": "%s"
                        }
                        """.formatted(expiredIdToken))
                .when()
                .post("/api/v1/auth/kakao")
                .then()
                .statusCode(401)
                .body("error.code", is("AUTH_001"));
    }

    @Test
    @DisplayName("공격자 키로 위조 서명된 ID Token은 401로 거부된다")
    void kakaoLogin_forgedSignature_returns401() {
        // given - JWKS에는 키페어 공개키만 등록되어 있는데 attackerKeyPair 개인키로 서명
        final Date now = new Date();
        final Date expiry = new Date(now.getTime() + TOKEN_EXPIRY_MILLIS);
        final String forgedIdToken = buildIdToken(attackerKeyPair, TEST_KID, TEST_ISSUER, TEST_AUDIENCE,
                "44444", "공격자", now, expiry);

        // when & then
        RestAssured.given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "idToken": "%s"
                        }
                        """.formatted(forgedIdToken))
                .when()
                .post("/api/v1/auth/kakao")
                .then()
                .statusCode(401)
                .body("error.code", is("AUTH_001"));
    }

    @Test
    @DisplayName("idToken 필드가 누락된 요청은 인증 실패로 거부된다")
    void kakaoLogin_missingIdToken_rejected() {
        // given & when
        final int statusCode = RestAssured.given()
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .post("/api/v1/auth/kakao")
                .then()
                .extract()
                .statusCode();

        // then - 400(유효성 검증) 또는 401(인증 실패) 둘 다 허용 (Security 설정에 따라 다름)
        assertThat(statusCode).isIn(400, 401);
    }

    private void stubJwksEndpoint() {
        wireMockServer.stubFor(get(urlPathEqualTo("/.well-known/jwks.json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jwksJson)));
    }

    private static String buildIdToken(final String sub, final String nickname) {
        final Date now = new Date();
        final Date expiry = new Date(now.getTime() + TOKEN_EXPIRY_MILLIS);
        return buildIdToken(keyPair, TEST_KID, TEST_ISSUER, TEST_AUDIENCE, sub, nickname, now, expiry);
    }

    private static String buildIdToken(
            final KeyPair signingKeyPair,
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
                .signWith(signingKeyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
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
