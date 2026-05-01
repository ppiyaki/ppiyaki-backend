package com.ppiyaki.pet.controller;

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
class PetControllerE2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        jdbcTemplate.update("UPDATE users SET pet = NULL WHERE login_id = 'pet_e2e_user'");
        jdbcTemplate.update("DELETE FROM pets WHERE id IN "
                + "(SELECT pet FROM users WHERE login_id = 'pet_e2e_user')");
        jdbcTemplate.update("DELETE FROM refresh_tokens WHERE user_id IN "
                + "(SELECT id FROM users WHERE login_id = 'pet_e2e_user')");
        jdbcTemplate.update("DELETE FROM users WHERE login_id = 'pet_e2e_user'");
    }

    @Test
    @DisplayName("유저가 펫을 조회하면 포인트와 레벨을 반환한다")
    void readMyPet_success() {
        // given — 회원가입
        final String accessToken = RestAssured.given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "loginId": "pet_e2e_user",
                            "password": "pass1234!",
                            "nickname": "펫유저"
                        }
                        """)
                .when()
                .post("/api/v1/auth/signup")
                .then()
                .statusCode(201)
                .extract()
                .path("accessToken");

        // given — 펫 생성 및 유저에 연결 (DB 직접)
        jdbcTemplate.update("INSERT INTO pets (point, created_at, updated_at) VALUES (40, NOW(), NOW())");
        final Long petId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM pets", Long.class);
        jdbcTemplate.update("UPDATE users SET pet = ? WHERE login_id = 'pet_e2e_user'", petId);

        // when & then
        RestAssured.given()
                .header("Authorization", "Bearer " + accessToken)
                .when()
                .get("/api/v1/pets/me")
                .then()
                .statusCode(200)
                .body("point", is(40))
                .body("level", is(2));
    }
}
