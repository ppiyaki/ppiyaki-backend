package com.ppiyaki.common.exception;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("404 응답 정규화 E2E")
class GlobalExceptionHandlerNotFoundE2ETest {

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    @DisplayName("permitAll 경로 내 미존재 GET → 404 NOT_FOUND")
    void unknown_get_path_returns_404() {
        RestAssured.given()
                .when()
                .get("/api/v1/auth/this-endpoint-does-not-exist")
                .then()
                .statusCode(404)
                .body("error.code", is("COMMON_005"))
                .body("error.message", containsString("auth/this-endpoint-does-not-exist"));
    }

    @Test
    @DisplayName("permitAll 경로 내 미존재 POST (presigned-url 오타 케이스 모사) → 404 NOT_FOUND")
    void unknown_post_path_returns_404() {
        RestAssured.given()
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .post("/api/v1/auth/non-existent-resource-url")
                .then()
                .statusCode(404)
                .body("error.code", is("COMMON_005"))
                .body("error.message", containsString("auth/non-existent-resource-url"));
    }

    @Test
    @DisplayName("인증된 사용자가 미존재 경로 호출 시 → 404 NOT_FOUND (운영 시나리오)")
    void authenticated_unknown_path_returns_404() {
        final String accessToken = signupAndGetToken();

        RestAssured.given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .post("/api/v1/uploads/presigned-url")
                .then()
                .statusCode(404)
                .body("error.code", is("COMMON_005"))
                .body("error.message", containsString("uploads/presigned-url"));
    }

    private String signupAndGetToken() {
        final String loginId = "notfound" + System.nanoTime();
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
