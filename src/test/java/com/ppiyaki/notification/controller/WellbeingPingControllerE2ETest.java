package com.ppiyaki.notification.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyLong;

import com.ppiyaki.common.auth.JwtProvider;
import com.ppiyaki.notification.service.WellbeingPingCooldownStore;
import com.ppiyaki.user.domain.User;
import com.ppiyaki.user.domain.UserRole;
import com.ppiyaki.user.repository.UserRepository;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("POST /api/v1/notifications/wellbeing-pings E2E")
class WellbeingPingControllerE2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JwtProvider jwtProvider;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockBean
    private WellbeingPingCooldownStore cooldownStore;

    private static long userSequence = 900000L;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        Mockito.when(cooldownStore.tryAcquire(anyLong(), anyLong())).thenReturn(true);
    }

    @Test
    @DisplayName("시니어가 CareRelation 있는 보호자에게 안부 발송 → 204")
    void wellbeing_ping_success() {
        final Long seniorId = seedSenior();
        final Long caregiverId = seedCaregiver();
        seedCareRelation(seniorId, caregiverId);
        seedDeviceToken(caregiverId, "fcm-wp-" + caregiverId);

        final String seniorToken = jwtProvider.createAccessToken(seniorId, UserRole.SENIOR.name());

        RestAssured.given()
                .header("Authorization", "Bearer " + seniorToken)
                .contentType(ContentType.JSON)
                .body("""
                        {"caregiverId": %d}
                        """.formatted(caregiverId))
                .when()
                .post("/api/v1/notifications/wellbeing-pings")
                .then()
                .statusCode(204);
    }

    @Test
    @DisplayName("쿨다운 차단 시 429 + Retry-After 헤더")
    void wellbeing_ping_cooldown_blocked() {
        final Long seniorId = seedSenior();
        final Long caregiverId = seedCaregiver();
        seedCareRelation(seniorId, caregiverId);

        Mockito.when(cooldownStore.tryAcquire(seniorId, caregiverId)).thenReturn(false);
        Mockito.when(cooldownStore.getRetryAfterSeconds(seniorId, caregiverId))
                .thenReturn(Optional.of(42L));

        final String seniorToken = jwtProvider.createAccessToken(seniorId, UserRole.SENIOR.name());

        RestAssured.given()
                .header("Authorization", "Bearer " + seniorToken)
                .contentType(ContentType.JSON)
                .body("""
                        {"caregiverId": %d}
                        """.formatted(caregiverId))
                .when()
                .post("/api/v1/notifications/wellbeing-pings")
                .then()
                .statusCode(429)
                .header("Retry-After", "42")
                .body("error.code", is("COMMON_006"));
    }

    @Test
    @DisplayName("CareRelation 없는 보호자에게 발송 → 403 CARE_001")
    void wellbeing_ping_no_care_relation() {
        final Long seniorId = seedSenior();
        final Long caregiverId = seedCaregiver();
        // No care_relations INSERT

        final String seniorToken = jwtProvider.createAccessToken(seniorId, UserRole.SENIOR.name());

        RestAssured.given()
                .header("Authorization", "Bearer " + seniorToken)
                .contentType(ContentType.JSON)
                .body("""
                        {"caregiverId": %d}
                        """.formatted(caregiverId))
                .when()
                .post("/api/v1/notifications/wellbeing-pings")
                .then()
                .statusCode(403)
                .body("error.code", is("CARE_001"));
    }

    @Test
    @DisplayName("보호자 JWT로 호출 → 403 CARE_008 역할 mismatch")
    void wellbeing_ping_caller_not_senior() {
        final Long callerCaregiverId = seedCaregiver();
        final Long targetCaregiverId = seedCaregiver();

        final String caregiverToken = jwtProvider.createAccessToken(
                callerCaregiverId, UserRole.CAREGIVER.name());

        RestAssured.given()
                .header("Authorization", "Bearer " + caregiverToken)
                .contentType(ContentType.JSON)
                .body("""
                        {"caregiverId": %d}
                        """.formatted(targetCaregiverId))
                .when()
                .post("/api/v1/notifications/wellbeing-pings")
                .then()
                .statusCode(403)
                .body("error.code", is("CARE_008"));
    }

    // --- fixtures ---

    private Long seedSenior() {
        return transactionTemplate.execute(status -> {
            final User senior = User.createSenior("WP시니어" + userSequence++, (LocalDate) null);
            return userRepository.save(senior).getId();
        });
    }

    private Long seedCaregiver() {
        final long seq = userSequence++;
        return transactionTemplate.execute(status -> {
            final User caregiver = User.createSenior("WP보호자" + seq, (LocalDate) null);
            caregiver.assignRole(UserRole.CAREGIVER);
            return userRepository.save(caregiver).getId();
        });
    }

    private void seedCareRelation(final Long seniorId, final Long caregiverId) {
        jdbcTemplate.update(
                "INSERT INTO care_relations (senior_id, caregiver_id, created_at, updated_at) "
                        + "VALUES (?, ?, NOW(6), NOW(6))",
                seniorId, caregiverId);
    }

    private void seedDeviceToken(final Long userId, final String token) {
        jdbcTemplate.update(
                "INSERT INTO device_tokens "
                        + "(user_id, token, platform, is_active, created_at, updated_at) "
                        + "VALUES (?, ?, 'ANDROID', TRUE, NOW(6), NOW(6))",
                userId, token);
    }
}
