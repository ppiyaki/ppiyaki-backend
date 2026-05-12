package com.ppiyaki.common.exception;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("405 응답 정규화 E2E")
class GlobalExceptionHandlerMethodNotAllowedE2ETest {

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    @DisplayName("permitAll POST-only 경로에 GET 호출 → 405 METHOD_NOT_ALLOWED + Allow 헤더")
    void wrong_method_on_permitall_path_returns_405() {
        RestAssured.given()
                .when()
                .get("/api/v1/auth/signup")
                .then()
                .statusCode(405)
                .header("Allow", containsString("POST"))
                .body("error.code", is("COMMON_007"))
                .body("error.message", containsString("GET"));
    }

    @Test
    @DisplayName("인증된 사용자가 GET-only 경로에 POST 호출 → 405 METHOD_NOT_ALLOWED + Allow 헤더")
    void wrong_method_on_authenticated_path_returns_405() {
        final String accessToken = signupAndGetToken();

        RestAssured.given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .post("/api/v1/users/me")
                .then()
                .statusCode(405)
                .header("Allow", notNullValue())
                .body("error.code", is("COMMON_007"))
                .body("error.message", containsString("POST"));
    }

    private String signupAndGetToken() {
        final String loginId = "method405" + System.nanoTime();
        final String response = RestAssured.given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "loginId": "%s",
                            "password": "password1234!",
                            "nickname": "테스트"
                        }
                        """.formatted(loginId))
                .when()
                .post("/api/v1/auth/signup")
                .then()
                .statusCode(201)
                .extract()
                .asString();
        return io.restassured.path.json.JsonPath.from(response).getString("accessToken");
    }
}
