package com.ppiyaki.user.controller;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "kakao.oidc.issuer=https://kauth.kakao.com",
        "kakao.oidc.jwks-uri=http://localhost:19878/.well-known/jwks.json",
        "kakao.oidc.audience=test-app-key"
})
class LocalAuthE2ETest {

    @LocalServerPort
    private int port;

    private static long idSequence = 300000L;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    @DisplayName("로컬 회원가입 시 201 응답과 JWT 발급, isOnboarded=false")
    void signup_success() {
        final String loginId = "user" + idSequence++;

        RestAssured.given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "loginId": "%s",
                            "password": "password123!",
                            "nickname": "테스트유저"
                        }
                        """.formatted(loginId))
                .when()
                .post("/api/v1/auth/signup")
                .then()
                .statusCode(201)
                .body("accessToken", notNullValue())
                .body("refreshToken", notNullValue())
                .body("isOnboarded", is(true));
    }

    @Test
    @DisplayName("중복 loginId로 가입 시 409 응답")
    void signup_duplicateLoginId() {
        final String loginId = "duplicate" + idSequence++;

        // given - 첫 가입
        RestAssured.given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "loginId": "%s",
                            "password": "password123!",
                            "nickname": "첫유저"
                        }
                        """.formatted(loginId))
                .when()
                .post("/api/v1/auth/signup")
                .then()
                .statusCode(201);

        // when - 중복 가입
        RestAssured.given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "loginId": "%s",
                            "password": "other456!",
                            "nickname": "둘째유저"
                        }
                        """.formatted(loginId))
                .when()
                .post("/api/v1/auth/signup")
                .then()
                .statusCode(409)
                .body("error.code", is("AUTH_003"));
    }

    @Test
    @DisplayName("로컬 로그인 성공 시 200 응답과 JWT 발급")
    void login_success() {
        final String loginId = "login" + idSequence++;

        // given - 가입
        RestAssured.given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "loginId": "%s",
                            "password": "mypassword!",
                            "nickname": "로그인유저"
                        }
                        """.formatted(loginId))
                .when()
                .post("/api/v1/auth/signup")
                .then()
                .statusCode(201);

        // when - 로그인
        RestAssured.given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "loginId": "%s",
                            "password": "mypassword!"
                        }
                        """.formatted(loginId))
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(200)
                .body("accessToken", notNullValue())
                .body("refreshToken", notNullValue())
                .body("isOnboarded", is(true));
    }

    @Test
    @DisplayName("잘못된 비밀번호로 로그인 시 401 응답")
    void login_wrongPassword() {
        final String loginId = "wrongpw" + idSequence++;

        // given - 가입
        RestAssured.given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "loginId": "%s",
                            "password": "correctpw!",
                            "nickname": "비번유저"
                        }
                        """.formatted(loginId))
                .when()
                .post("/api/v1/auth/signup")
                .then()
                .statusCode(201);

        // when - 틀린 비밀번호
        RestAssured.given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "loginId": "%s",
                            "password": "wrongpw!"
                        }
                        """.formatted(loginId))
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(401)
                .body("error.code", is("AUTH_004"));
    }

    @Test
    @DisplayName("존재하지 않는 loginId로 로그인 시 401 응답")
    void login_nonexistentUser() {
        RestAssured.given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "loginId": "nonexistent999",
                            "password": "anything!"
                        }
                        """)
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(401)
                .body("error.code", is("AUTH_004"));
    }
}
