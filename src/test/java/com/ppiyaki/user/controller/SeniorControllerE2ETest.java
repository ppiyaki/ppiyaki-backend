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
class SeniorControllerE2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        jdbcTemplate.update("DELETE FROM care_relations WHERE caregiver_id IN "
                + "(SELECT id FROM users WHERE login_id = 'cg_senior_e2e')");
        jdbcTemplate.update("DELETE FROM refresh_tokens WHERE user_id IN "
                + "(SELECT id FROM users WHERE login_id = 'cg_senior_e2e')");
        jdbcTemplate.update("DELETE FROM pets WHERE id IN "
                + "(SELECT pet FROM users WHERE nickname = '시니어E2E대리' AND pet IS NOT NULL)");
        jdbcTemplate.update("DELETE FROM users WHERE nickname = '시니어E2E대리'");
        jdbcTemplate.update("DELETE FROM users WHERE login_id = 'cg_senior_e2e'");
    }

    @Test
    @DisplayName("보호자가 시니어를 대리 생성하면 시니어 계정과 CareRelation, Pet이 생성된다")
    void createSenior_success() {
        // given — 보호자 회원가입 (role=CAREGIVER 자동)
        final String caregiverToken = RestAssured.given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "loginId": "cg_senior_e2e",
                            "password": "pass1234!",
                            "nickname": "보호자E2E"
                        }
                        """)
                .when()
                .post("/api/v1/auth/signup")
                .then()
                .statusCode(201)
                .extract()
                .path("accessToken");

        // when — 시니어 대리 생성
        RestAssured.given()
                .header("Authorization", "Bearer " + caregiverToken)
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "nickname": "시니어E2E대리",
                            "dob": "1945-03-15"
                        }
                        """)
                .when()
                .post("/api/v1/seniors")
                .then()
                .statusCode(201)
                .body("seniorId", notNullValue())
                .body("careRelationId", notNullValue())
                .body("petId", notNullValue());
    }
}
