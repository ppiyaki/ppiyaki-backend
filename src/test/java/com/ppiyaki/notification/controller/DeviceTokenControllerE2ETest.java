package com.ppiyaki.notification.controller;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DeviceTokenControllerE2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        jdbcTemplate.update("DELETE FROM device_tokens WHERE user_id IN "
                + "(SELECT id FROM users WHERE login_id IN ('dt_owner', 'dt_other'))");
        jdbcTemplate.update("DELETE FROM refresh_tokens WHERE user_id IN "
                + "(SELECT id FROM users WHERE login_id IN ('dt_owner', 'dt_other'))");
        jdbcTemplate.update("DELETE FROM users WHERE login_id IN ('dt_owner', 'dt_other')");
    }

    @Test
    @DisplayName("device token 등록 + 동일 token 재요청 멱등 + 본인 해제 + 타인 해제 시 403")
    void device_token_flow() {
        final String ownerToken = signupAndGetToken("dt_owner");
        final String otherToken = signupAndGetToken("dt_other");

        // when — 신규 등록
        final Integer tokenId = RestAssured.given()
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(ContentType.JSON)
                .body("""
                        {"token": "fcm-token-abc", "platform": "ANDROID"}
                        """)
                .when()
                .post("/api/v1/users/me/devices")
                .then()
                .statusCode(201)
                .body("tokenId", notNullValue())
                .body("platform", is("ANDROID"))
                .body("isActive", is(true))
                .extract()
                .path("tokenId");

        // when — 동일 token 재요청 → 멱등 (같은 row)
        RestAssured.given()
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(ContentType.JSON)
                .body("""
                        {"token": "fcm-token-abc", "platform": "ANDROID"}
                        """)
                .when()
                .post("/api/v1/users/me/devices")
                .then()
                .statusCode(201)
                .body("tokenId", is(tokenId));

        final Integer rowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM device_tokens WHERE token = ?", Integer.class, "fcm-token-abc");
        assert rowCount != null && rowCount == 1;

        // when — 본인 해제
        RestAssured.given()
                .header("Authorization", "Bearer " + ownerToken)
                .when()
                .delete("/api/v1/users/me/devices/" + tokenId)
                .then()
                .statusCode(204);

        final Boolean isActive = jdbcTemplate.queryForObject(
                "SELECT is_active FROM device_tokens WHERE id = ?", Boolean.class, tokenId.longValue());
        assert isActive != null && !isActive;

        // when — 타인 해제 시 403 NOTIFICATION_004
        RestAssured.given()
                .header("Authorization", "Bearer " + otherToken)
                .when()
                .delete("/api/v1/users/me/devices/" + tokenId)
                .then()
                .statusCode(403)
                .body("error.code", is("NOTIFICATION_004"));
    }

    private String signupAndGetToken(final String loginId) {
        return RestAssured.given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "loginId": "%s",
                            "password": "pass1234!",
                            "nickname": "%s"
                        }
                        """.formatted(loginId, loginId))
                .when()
                .post("/api/v1/auth/signup")
                .then()
                .statusCode(201)
                .extract()
                .path("accessToken");
    }
}
