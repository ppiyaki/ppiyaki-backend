package com.ppiyaki.notification.controller;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;

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
class NotificationSettingsControllerE2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        jdbcTemplate.update("DELETE FROM notification_settings WHERE caregiver_id IN "
                + "(SELECT id FROM users WHERE login_id IN ('ns_owner', 'ns_other'))");
        jdbcTemplate.update("DELETE FROM care_relations WHERE caregiver_id IN "
                + "(SELECT id FROM users WHERE login_id IN ('ns_owner', 'ns_other'))");
        jdbcTemplate.update("DELETE FROM pets WHERE id IN "
                + "(SELECT pet FROM users WHERE nickname = 'NS시니어' AND pet IS NOT NULL)");
        jdbcTemplate.update("DELETE FROM users WHERE nickname = 'NS시니어'");
        jdbcTemplate.update("DELETE FROM refresh_tokens WHERE user_id IN "
                + "(SELECT id FROM users WHERE login_id IN ('ns_owner', 'ns_other'))");
        jdbcTemplate.update("DELETE FROM users WHERE login_id IN ('ns_owner', 'ns_other')");
    }

    @Test
    @DisplayName("조회 + PUT(mode→CUSTOM) + 프리셋 적용(INTENSIVE) + 다른 보호자 접근 시 403")
    void notification_settings_flow() {
        final String ownerToken = signupAndGetToken("ns_owner");
        final String otherToken = signupAndGetToken("ns_other");
        final Long seniorId = onboardSenior(ownerToken);

        // when — 조회 (onboarding 시 STANDARD 프리셋으로 자동 생성됨)
        RestAssured.given()
                .header("Authorization", "Bearer " + ownerToken)
                .when()
                .get("/api/v1/seniors/" + seniorId + "/notification-settings")
                .then()
                .statusCode(200)
                .body("seniorId", is(seniorId.intValue()))
                .body("medicationDelayThresholdMinutes", is(60))
                .body("familySafetyThresholdHours", is(48))
                .body("medicationCompleteEnabled", is(false));

        // when — PUT 갱신 (사용자 직접 수정)
        RestAssured.given()
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "durWarningEnabled": true,
                          "medicationDelayEnabled": true,
                          "medicationDelayThresholdMinutes": 45,
                          "familySafetyEnabled": false,
                          "familySafetyThresholdHours": 24,
                          "medicationCompleteEnabled": true
                        }
                        """)
                .when()
                .put("/api/v1/seniors/" + seniorId + "/notification-settings")
                .then()
                .statusCode(200)
                .body("medicationDelayThresholdMinutes", is(45))
                .body("familySafetyEnabled", is(false))
                .body("medicationCompleteEnabled", is(true));

        // when — 프리셋 적용 (INTENSIVE)
        RestAssured.given()
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(ContentType.JSON)
                .body("""
                        {"mode": "INTENSIVE"}
                        """)
                .when()
                .post("/api/v1/seniors/" + seniorId + "/notification-settings/preset")
                .then()
                .statusCode(200)
                .body("medicationDelayThresholdMinutes", is(30))
                .body("familySafetyThresholdHours", is(12))
                .body("medicationCompleteEnabled", is(true));

        // when — 다른 보호자 접근 → 403 CARE_001
        RestAssured.given()
                .header("Authorization", "Bearer " + otherToken)
                .when()
                .get("/api/v1/seniors/" + seniorId + "/notification-settings")
                .then()
                .statusCode(403)
                .body("error.code", is("CARE_001"));
    }

    private Long onboardSenior(final String accessToken) {
        return ((Number) RestAssured.given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "nickname": "보호자온보딩",
                          "seniors": [
                            {"nickname": "NS시니어", "gender": "FEMALE", "notificationMode": "STANDARD"}
                          ]
                        }
                        """)
                .when()
                .post("/api/v1/onboarding")
                .then()
                .statusCode(201)
                .body("responses[0].seniorId", greaterThan(0))
                .extract()
                .path("responses[0].seniorId")).longValue();
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
