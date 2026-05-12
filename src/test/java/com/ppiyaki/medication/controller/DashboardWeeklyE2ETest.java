package com.ppiyaki.medication.controller;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import com.ppiyaki.user.User;
import com.ppiyaki.user.repository.UserRepository;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "clova.ocr.secret=test-secret",
        "clova.ocr.invoke-url=https://test.example.com/clova-ocr",
        "openai.api-key=sk-test-placeholder",
        "openai.model=gpt-test",
        "mfds.api.service-key=test-service-key",
        "mfds.api.base-url=test.example.com/mfds",
        "mfds.api.connect-timeout=2000",
        "mfds.api.read-timeout=5000",
        "ncp.storage.endpoint=https://kr.object.ncloudstorage.com",
        "ncp.storage.region=kr-standard",
        "ncp.storage.access-key=test-access-key",
        "ncp.storage.secret-key=test-secret-key",
        "ncp.storage.bucket-name=ppiyaki-test"
})
@DisplayName("GET /api/v1/seniors/{seniorId}/dashboard/weekly E2E")
class DashboardWeeklyE2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private static long userSequence = 800000L;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    @DisplayName("시니어 본인 호출 — 가입 후 날짜 schedule 없으면 PERTECT, 가입 이전은 NOT_SCHEDULED (issue #326)")
    void weekly_seniorSelf_emptySchedules() {
        final SignupResult senior = signup("주간시니어");
        setMealTimes(senior.userId(), LocalTime.of(8, 0), LocalTime.of(12, 30), LocalTime.of(18, 30));
        // weekStart를 today로 두어 days[0]이 가입 직후(또는 가입일과 동일)가 되도록 함
        final LocalDate weekStart = LocalDate.now();

        RestAssured.given()
                .header("Authorization", "Bearer " + senior.accessToken())
                .when()
                .get("/api/v1/seniors/" + senior.userId() + "/dashboard/weekly?weekStart=" + weekStart)
                .then()
                .statusCode(200)
                .body("seniorId", is(senior.userId().intValue()))
                .body("weekStart", is(weekStart.toString()))
                .body("weekEnd", is(weekStart.plusDays(6).toString()))
                .body("adherenceRate", is(nullValue()))
                .body("days.size()", is(7))
                .body("days[0].dayStatus", is("PERFECT"))
                .body("days[0].slots", notNullValue())
                .body("days[0].slots[0].status", is("NOT_SCHEDULED"));
    }

    @Test
    @DisplayName("관계 없는 사용자 호출 → 403 CARE_RELATION_NOT_FOUND")
    void weekly_unrelatedUser_returns403() {
        final SignupResult senior = signup("주간타인시니어");
        final SignupResult intruder = signup("주간외부인");
        final LocalDate weekStart = LocalDate.now().minusDays(7);

        RestAssured.given()
                .header("Authorization", "Bearer " + intruder.accessToken())
                .when()
                .get("/api/v1/seniors/" + senior.userId() + "/dashboard/weekly?weekStart=" + weekStart)
                .then()
                .statusCode(403)
                .body("error.code", is("CARE_001"));
    }

    private SignupResult signup(final String nickname) {
        final String loginId = "dashweekly" + userSequence++;
        final String response = RestAssured.given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "loginId": "%s",
                            "password": "password1234!",
                            "nickname": "%s"
                        }
                        """.formatted(loginId, nickname))
                .when()
                .post("/api/v1/auth/signup")
                .then()
                .statusCode(201)
                .extract()
                .asString();
        final Long userId = userRepository.findByLoginId(loginId).orElseThrow().getId();
        final String accessToken = io.restassured.path.json.JsonPath.from(response).getString("accessToken");
        return new SignupResult(userId, accessToken);
    }

    private void setMealTimes(
            final Long userId,
            final LocalTime breakfast,
            final LocalTime lunch,
            final LocalTime dinner
    ) {
        transactionTemplate.executeWithoutResult(status -> {
            final User user = userRepository.findById(userId).orElseThrow();
            setHierarchicalField(user, "breakfastTime", breakfast);
            setHierarchicalField(user, "lunchTime", lunch);
            setHierarchicalField(user, "dinnerTime", dinner);
            userRepository.save(user);
        });
    }

    private static void setHierarchicalField(final Object target, final String fieldName, final Object value) {
        Class<?> clazz = target.getClass();
        while (clazz != null) {
            try {
                final Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (final NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            } catch (final IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalStateException("Field not found: " + fieldName);
    }

    private record SignupResult(Long userId, String accessToken) {
    }
}
