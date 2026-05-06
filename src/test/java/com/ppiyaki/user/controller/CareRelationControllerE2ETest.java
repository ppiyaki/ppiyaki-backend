package com.ppiyaki.user.controller;

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
class CareRelationControllerE2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        jdbcTemplate.update("DELETE FROM invite_codes WHERE senior_id IN "
                + "(SELECT id FROM users WHERE nickname = '시니어코드E2E')");
        jdbcTemplate.update("DELETE FROM care_relations WHERE caregiver_id IN "
                + "(SELECT id FROM users WHERE login_id = 'cg_code_e2e')");
        jdbcTemplate.update("DELETE FROM refresh_tokens WHERE user_id IN "
                + "(SELECT id FROM users WHERE login_id = 'cg_code_e2e')");
        jdbcTemplate.update("DELETE FROM refresh_tokens WHERE user_id IN "
                + "(SELECT id FROM users WHERE nickname = '시니어코드E2E')");
        jdbcTemplate.update("DELETE FROM pets WHERE id IN "
                + "(SELECT pet FROM users WHERE nickname = '시니어코드E2E' AND pet IS NOT NULL)");
        jdbcTemplate.update("DELETE FROM users WHERE nickname = '시니어코드E2E'");
        jdbcTemplate.update("DELETE FROM users WHERE login_id = 'cg_code_e2e'");
    }

    @Test
    @DisplayName("보호자가 시니어 생성 후 초대 코드를 발급하고 코드 로그인이 성공한다")
    void inviteAndCodeLogin_success() {
        // given — 보호자 회원가입
        final String caregiverToken = RestAssured.given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "loginId": "cg_code_e2e",
                            "password": "pass1234!",
                            "nickname": "보호자코드E2E"
                        }
                        """)
                .when()
                .post("/api/v1/auth/signup")
                .then()
                .statusCode(201)
                .extract()
                .path("accessToken");

        // given — 시니어 대리 생성
        final Integer seniorId = RestAssured.given()
                .header("Authorization", "Bearer " + caregiverToken)
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "nickname": "��니어코드E2E",
                            "dob": "1945-03-15"
                        }
                        """)
                .when()
                .post("/api/v1/seniors")
                .then()
                .statusCode(201)
                .extract()
                .path("seniorId");

        // when — 보호자��� 초대 코드 발급
        final String inviteCode = RestAssured.given()
                .header("Authorization", "Bearer " + caregiverToken)
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "seniorId": %d
                        }
                        """.formatted(seniorId))
                .when()
                .post("/api/v1/care-relations/invite")
                .then()
                .statusCode(201)
                .body("inviteCode", notNullValue())
                .extract()
                .path("inviteCode");

        // then — 시니어가 코드로 로그인
        RestAssured.given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "code": "%s"
                        }
                        """.formatted(inviteCode))
                .when()
                .post("/api/v1/auth/code-login")
                .then()
                .statusCode(200)
                .body("accessToken", notNullValue())
                .body("refreshToken", notNullValue())
                .body("isOnboarded", is(true));
    }
}
