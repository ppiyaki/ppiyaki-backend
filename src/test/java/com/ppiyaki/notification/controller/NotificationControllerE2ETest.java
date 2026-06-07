package com.ppiyaki.notification.controller;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
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
class NotificationControllerE2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        jdbcTemplate.update("DELETE FROM notifications WHERE user_id IN "
                + "(SELECT id FROM users WHERE login_id IN ('noti_owner', 'noti_other'))");
        jdbcTemplate.update("DELETE FROM refresh_tokens WHERE user_id IN "
                + "(SELECT id FROM users WHERE login_id IN ('noti_owner', 'noti_other'))");
        jdbcTemplate.update("DELETE FROM users WHERE login_id IN ('noti_owner', 'noti_other')");
    }

    @Test
    @DisplayName("본인 알림함 조회 + 단건 읽음 + 모두 읽음 + 타인 알림 접근 시 403")
    void notification_flow() {
        // given — 두 사용자 회원가입
        final String ownerToken = signupAndGetToken("noti_owner");
        final String otherToken = signupAndGetToken("noti_other");
        final Long ownerId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE login_id = 'noti_owner'", Long.class);
        final Long otherId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE login_id = 'noti_other'", Long.class);

        // owner 알림 2건 + other 알림 1건 INSERT
        jdbcTemplate.update("INSERT INTO notifications "
                + "(user_id, category, title, body, meal_slot, target_date, created_at) "
                + "VALUES (?, 'MEDICATION_REMINDER', '아침 약 복용', '아침 약 드세요', 'BREAKFAST', CURDATE(), NOW(6))",
                ownerId);
        jdbcTemplate.update("INSERT INTO notifications "
                + "(user_id, category, title, body, created_at) "
                + "VALUES (?, 'MEDICATION_REMINDER', '저녁 약 복용', '저녁 약 드세요', NOW(6))", ownerId);
        jdbcTemplate.update("INSERT INTO notifications "
                + "(user_id, category, title, body, created_at) "
                + "VALUES (?, 'MEDICATION_REMINDER', 'other notification', 'other body', NOW(6))", otherId);

        final Long ownerNotificationId = jdbcTemplate.queryForObject(
                "SELECT id FROM notifications WHERE user_id = ? ORDER BY id ASC LIMIT 1",
                Long.class, ownerId);
        final Long otherNotificationId = jdbcTemplate.queryForObject(
                "SELECT id FROM notifications WHERE user_id = ?", Long.class, otherId);

        // when & then — owner가 자기 알림 2건만 조회
        RestAssured.given()
                .header("Authorization", "Bearer " + ownerToken)
                .when()
                .get("/api/v1/notifications")
                .then()
                .statusCode(200)
                .body("responses.size()", greaterThanOrEqualTo(2))
                .body("responses.mealSlot", hasItem("BREAKFAST"))
                .body("hasNext", is(false));

        // 단건 읽음
        RestAssured.given()
                .header("Authorization", "Bearer " + ownerToken)
                .when()
                .patch("/api/v1/notifications/" + ownerNotificationId + "/read")
                .then()
                .statusCode(204);

        final Integer readCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE id = ? AND read_at IS NOT NULL",
                Integer.class, ownerNotificationId);
        assert readCount != null && readCount == 1;

        // 모두 읽음
        RestAssured.given()
                .header("Authorization", "Bearer " + ownerToken)
                .when()
                .post("/api/v1/notifications/read-all")
                .then()
                .statusCode(204);

        final Integer ownerUnread = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND read_at IS NULL",
                Integer.class, ownerId);
        assert ownerUnread != null && ownerUnread == 0;

        // 타인 알림 PATCH 시 403 NOTIFICATION_FORBIDDEN
        RestAssured.given()
                .header("Authorization", "Bearer " + ownerToken)
                .when()
                .patch("/api/v1/notifications/" + otherNotificationId + "/read")
                .then()
                .statusCode(403)
                .body("error.code", is("NOTIFICATION_002"));
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
