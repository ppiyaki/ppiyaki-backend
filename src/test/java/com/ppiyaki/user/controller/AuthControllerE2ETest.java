package com.ppiyaki.user.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
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
    private static String jwksJson;

    @LocalServerPort
    private int port;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().port(WIREMOCK_PORT));
        wireMockServer.start();
        keyPair = Jwts.SIG.RS256.keyPair().build();
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
    @DisplayName("신규 유저 카카오 OIDC 로그인 시 JWT 발급 및 isOnboarded=false")
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
                .body("isOnboarded", is(false));
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
                .body("isOnboarded", is(false));
    }

    @Test
    @DisplayName("인증 없이 호출하면 401")
    void me_unauthorized() {
        RestAssured.given()
                .when()
                .get("/api/v1/users/me")
                .then()
                .statusCode(401);
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
        return Jwts.builder()
                .header().keyId(TEST_KID).and()
                .issuer(TEST_ISSUER)
                .audience().add(TEST_AUDIENCE).and()
                .subject(sub)
                .claim("nickname", nickname)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
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
