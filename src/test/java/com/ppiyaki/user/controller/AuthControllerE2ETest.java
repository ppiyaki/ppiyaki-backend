package com.ppiyaki.user.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "kakao.client-id=test-client-id",
        "kakao.client-secret=test-client-secret",
        "kakao.token-uri=http://localhost:19876/oauth/token",
        "kakao.user-info-uri=http://localhost:19876/v2/user/me"
})
class AuthControllerE2ETest {

    private static final int WIREMOCK_PORT = 19876;
    private static WireMockServer wireMockServer;

    @LocalServerPort
    private int port;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().port(WIREMOCK_PORT));
        wireMockServer.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMockServer.stop();
    }

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        wireMockServer.resetAll();
        stubKakaoTokenEndpoint();
    }

    @Test
    @DisplayName("신규 유저 카카오 로그인 시 JWT 발급 및 isOnboarded=false")
    void kakaoLogin_newUser() {
        // given
        stubKakaoUserInfoEndpoint(12345L, "테스트닉네임");

        // when & then
        RestAssured.given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "code": "test-auth-code",
                            "redirectUri": "http://localhost/callback"
                        }
                        """)
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
        stubKakaoUserInfoEndpoint(67890L, "기존유저");

        RestAssured.given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "code": "test-auth-code",
                            "redirectUri": "http://localhost/callback"
                        }
                        """)
                .when()
                .post("/api/v1/auth/kakao")
                .then()
                .statusCode(200);

        // when - 재로그인
        RestAssured.given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "code": "test-auth-code-2",
                            "redirectUri": "http://localhost/callback"
                        }
                        """)
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
        stubKakaoUserInfoEndpoint(99999L, "리프레시유저");

        final String refreshToken = RestAssured.given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "code": "test-auth-code",
                            "redirectUri": "http://localhost/callback"
                        }
                        """)
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
        stubKakaoUserInfoEndpoint(77777L, "내정보유저");

        final String accessToken = RestAssured.given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "code": "test-auth-code",
                            "redirectUri": "http://localhost/callback"
                        }
                        """)
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

    private void stubKakaoTokenEndpoint() {
        wireMockServer.stubFor(post(urlPathEqualTo("/oauth/token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "access_token": "kakao-access-token-mock",
                                    "token_type": "bearer",
                                    "expires_in": 3600
                                }
                                """)));
    }

    private void stubKakaoUserInfoEndpoint(final Long kakaoId, final String nickname) {
        wireMockServer.stubFor(get(urlPathEqualTo("/v2/user/me"))
                .withHeader("Authorization", equalTo("Bearer kakao-access-token-mock"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "id": %d,
                                    "kakao_account": {
                                        "profile": {
                                            "nickname": "%s"
                                        }
                                    }
                                }
                                """.formatted(kakaoId, nickname))));
    }
}
