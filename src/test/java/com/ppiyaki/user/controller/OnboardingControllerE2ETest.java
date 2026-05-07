package com.ppiyaki.user.controller;

import static org.hamcrest.Matchers.hasSize;
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
class OnboardingControllerE2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        jdbcTemplate.update("DELETE FROM care_relations WHERE caregiver_id IN "
                + "(SELECT id FROM users WHERE login_id = 'onboard_e2e')");
        jdbcTemplate.update("DELETE FROM pets WHERE id IN "
                + "(SELECT pet FROM users WHERE nickname IN ('온보딩할머니', '온보딩할아버지') AND pet IS NOT NULL)");
        jdbcTemplate.update("DELETE FROM refresh_tokens WHERE user_id IN "
                + "(SELECT id FROM users WHERE login_id = 'onboard_e2e')");
        jdbcTemplate.update("DELETE FROM users WHERE nickname IN ('온보딩할머니', '온보딩할아버지')");
        jdbcTemplate.update("DELETE FROM users WHERE login_id = 'onboard_e2e'");
    }

    @Test
    @DisplayName("보호자 온보딩 시 닉네임 변경 + 시니어 2명 생성된다")
    void onboard_success() {
        // given — 보호자 회원가입
        final String accessToken = RestAssured.given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "loginId": "onboard_e2e",
                            "password": "pass1234!",
                            "nickname": "임시닉네임"
                        }
                        """)
                .when()
                .post("/api/v1/auth/signup")
                .then()
                .statusCode(201)
                .extract()
                .path("accessToken");

        // when — 온보딩
        RestAssured.given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "nickname": "보호자온보딩",
                            "seniors": [
                                {
                                    "nickname": "온보딩할머니",
                                    "gender": "FEMALE",
                                    "notificationMode": "BASIC_ALERT"
                                },
                                {
                                    "nickname": "온보딩할아버지",
                                    "gender": "MALE",
                                    "notificationMode": "INTENSIVE_CARE"
                                }
                            ]
                        }
                        """)
                .when()
                .post("/api/v1/onboarding")
                .then()
                .statusCode(201)
                .body("caregiverNickname", is("보호자온보딩"))
                .body("seniors", hasSize(2))
                .body("seniors[0].nickname", is("온보딩할머니"))
                .body("seniors[0].seniorId", notNullValue())
                .body("seniors[0].petId", notNullValue())
                .body("seniors[1].nickname", is("온보딩할아버지"))
                .body("seniors[1].seniorId", notNullValue())
                .body("seniors[1].petId", notNullValue());
    }
}
