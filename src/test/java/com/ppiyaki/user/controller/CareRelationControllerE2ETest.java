package com.ppiyaki.user.controller;

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
class CareRelationControllerE2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    @DisplayName("보호자가 초대 코드를 발급하고 시니어가 수락하면 연동이 생성된다")
    void inviteAndAccept_success() {
        // given
        final String caregiverAccessToken = signupAndGetToken("caregiver_care_e2e", "pass1234!", "보호자E2E");
        setUserRole("caregiver_care_e2e", "CAREGIVER");

        final String seniorAccessToken = signupAndGetToken("senior_care_e2e", "pass1234!", "시니어E2E");
        setUserRole("senior_care_e2e", "SENIOR");

        // when — 보호자가 초대 코드 발급
        final String inviteCode = RestAssured.given()
                .header("Authorization", "Bearer " + caregiverAccessToken)
                .when()
                .post("/api/v1/care-relations/invite")
                .then()
                .statusCode(201)
                .body("inviteCode", notNullValue())
                .body("expiresAt", notNullValue())
                .extract()
                .path("inviteCode");

        // then — 시니어가 초대 코드로 연동 수락
        RestAssured.given()
                .header("Authorization", "Bearer " + seniorAccessToken)
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "inviteCode": "%s"
                        }
                        """.formatted(inviteCode))
                .when()
                .post("/api/v1/care-relations/accept")
                .then()
                .statusCode(200)
                .body("careRelationId", notNullValue())
                .body("seniorId", notNullValue())
                .body("caregiverId", notNullValue());
    }

    private String signupAndGetToken(final String loginId, final String password, final String nickname) {
        return RestAssured.given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "loginId": "%s",
                            "password": "%s",
                            "nickname": "%s"
                        }
                        """.formatted(loginId, password, nickname))
                .when()
                .post("/api/v1/auth/signup")
                .then()
                .statusCode(201)
                .extract()
                .path("accessToken");
    }

    private void setUserRole(final String loginId, final String role) {
        jdbcTemplate.update("UPDATE users SET role = ? WHERE login_id = ?", role, loginId);
    }
}
