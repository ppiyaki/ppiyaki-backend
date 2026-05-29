package com.ppiyaki.common.auth;

import static org.hamcrest.Matchers.is;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("CAREGIVER 전용 엔드포인트에 SENIOR 토큰 호출 → 403 FORBIDDEN E2E")
class RoleAuthorizationE2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private JwtProvider jwtProvider;

    private String seniorAccessToken;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        seniorAccessToken = jwtProvider.createAccessToken(999_999L, "SENIOR");
    }

    @Test
    @DisplayName("GET /api/v1/care-relations/seniors → 403")
    void readSeniors_with_senior_token_returns_403() {
        RestAssured.given()
                .header("Authorization", "Bearer " + seniorAccessToken)
                .when()
                .get("/api/v1/care-relations/seniors")
                .then()
                .statusCode(403)
                .body("error.code", is("COMMON_004"));
    }

    @Test
    @DisplayName("POST /api/v1/care-relations/invite → 403")
    void createInviteCode_with_senior_token_returns_403() {
        RestAssured.given()
                .header("Authorization", "Bearer " + seniorAccessToken)
                .contentType(ContentType.JSON)
                .body("{\"seniorId\": 1}")
                .when()
                .post("/api/v1/care-relations/invite")
                .then()
                .statusCode(403)
                .body("error.code", is("COMMON_004"));
    }

    @Test
    @DisplayName("DELETE /api/v1/seniors/{seniorId}/logout → 403")
    void forceLogoutSenior_with_senior_token_returns_403() {
        RestAssured.given()
                .header("Authorization", "Bearer " + seniorAccessToken)
                .when()
                .delete("/api/v1/seniors/1/logout")
                .then()
                .statusCode(403)
                .body("error.code", is("COMMON_004"));
    }

    @Test
    @DisplayName("POST /api/v1/seniors → 403")
    void createSenior_with_senior_token_returns_403() {
        RestAssured.given()
                .header("Authorization", "Bearer " + seniorAccessToken)
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "nickname": "할머니",
                            "gender": "FEMALE",
                            "careMode": "MANAGED"
                        }
                        """)
                .when()
                .post("/api/v1/seniors")
                .then()
                .statusCode(403)
                .body("error.code", is("COMMON_004"));
    }

    @Test
    @DisplayName("POST /api/v1/onboarding → 403")
    void onboard_with_senior_token_returns_403() {
        RestAssured.given()
                .header("Authorization", "Bearer " + seniorAccessToken)
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "nickname": "보호자",
                            "seniors": [
                                {"nickname": "할머니", "gender": "FEMALE", "careMode": "AUTONOMOUS"}
                            ]
                        }
                        """)
                .when()
                .post("/api/v1/onboarding")
                .then()
                .statusCode(403)
                .body("error.code", is("COMMON_004"));
    }
}
