package com.ppiyaki.medication.controller;

import static org.hamcrest.Matchers.is;

import com.ppiyaki.user.repository.UserRepository;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

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
@DisplayName("GET /api/v1/seniors/{seniorId}/dashboard/monthly E2E")
class DashboardMonthlyE2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    private static long userSequence = 900000L;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    @DisplayName("시니어 본인 호출 — 현재 월 schedule 없으면 가입일 이후 PERFECT, 가입 이전 NOT_SCHEDULED (issue #326)")
    void monthly_seniorSelf_emptySchedules() {
        final SignupResult senior = signup("월간시니어");
        // 현재 월로 조회 — 가입 후 days는 PERFECT, 가입 전 days는 NOT_SCHEDULED
        final YearMonth ym = YearMonth.now();
        final int today = LocalDate.now().getDayOfMonth();

        RestAssured.given()
                .header("Authorization", "Bearer " + senior.accessToken())
                .when()
                .get("/api/v1/seniors/" + senior.userId() + "/dashboard/monthly?yearMonth=" + ym)
                .then()
                .statusCode(200)
                .body("seniorId", is(senior.userId().intValue()))
                .body("yearMonth", is(ym.toString()))
                .body("days.size()", is(ym.lengthOfMonth()))
                // today는 가입일과 동일 → PERFECT (schedule 없음 + 가입 이후)
                .body("days[" + (today - 1) + "].dayStatus", is("PERFECT"))
                // 1일은 today보다 이르면 NOT_SCHEDULED, 같으면 PERFECT
                .body("days[0].dayStatus", is(today > 1 ? "NOT_SCHEDULED" : "PERFECT"));
    }

    @Test
    @DisplayName("관계 없는 사용자 호출 → 403 CARE_RELATION_NOT_FOUND")
    void monthly_unrelatedUser_returns403() {
        final SignupResult senior = signup("월간타인시니어");
        final SignupResult intruder = signup("월간외부인");
        final YearMonth ym = YearMonth.of(2026, 1);

        RestAssured.given()
                .header("Authorization", "Bearer " + intruder.accessToken())
                .when()
                .get("/api/v1/seniors/" + senior.userId() + "/dashboard/monthly?yearMonth=" + ym)
                .then()
                .statusCode(403)
                .body("error.code", is("CARE_001"));
    }

    private SignupResult signup(final String nickname) {
        final String loginId = "dashmonthly" + userSequence++;
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

    private record SignupResult(Long userId, String accessToken) {
    }
}
