package com.ppiyaki.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

import com.ppiyaki.user.domain.CareRelation;
import com.ppiyaki.user.repository.CareRelationRepository;
import com.ppiyaki.user.repository.UserRepository;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("GET /api/v1/timezones, PUT /api/v1/users/me/timezone, PUT /api/v1/users/{seniorId}/timezone E2E")
class TimezoneControllerE2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CareRelationRepository careRelationRepository;

    private static long userSequence = 910000L;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    @DisplayName("타임존 목록을 인증 없이 조회하면 200 + Asia/Seoul 포함")
    void read_timezones_success() {
        RestAssured.given()
                .when()
                .get("/api/v1/timezones")
                .then()
                .statusCode(200)
                .body("size()", greaterThan(0))
                .body("id", hasItem("Asia/Seoul"));
    }

    @Test
    @DisplayName("본인이 타임존을 PUT 하면 200 + 응답/DB 반영")
    void update_my_timezone_success() {
        // given
        final SignupResult user = signup("시니어A");

        // when & then
        RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + user.accessToken())
                .body("""
                        {
                            "timezone": "America/New_York"
                        }
                        """)
                .when()
                .put("/api/v1/users/me/timezone")
                .then()
                .statusCode(200)
                .body("id", equalTo(user.userId().intValue()))
                .body("timezone", is("America/New_York"));

        final var reloaded = userRepository.findById(user.userId()).orElseThrow();
        assertThat(reloaded.getTimezone()).isEqualTo("America/New_York");
    }

    @Test
    @DisplayName("지원 목록에 없는 타임존이면 400 COMMON_001")
    void unsupported_timezone_rejected() {
        // given
        final SignupResult user = signup("시니어B");

        // when & then
        RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + user.accessToken())
                .body("""
                        {
                            "timezone": "Mars/Olympus_Mons"
                        }
                        """)
                .when()
                .put("/api/v1/users/me/timezone")
                .then()
                .statusCode(400)
                .body("error.code", is("COMMON_001"));
    }

    @Test
    @DisplayName("타임존 누락 시 400")
    void timezone_missing_rejected() {
        // given
        final SignupResult user = signup("시니어C");

        // when & then
        RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + user.accessToken())
                .body("{}")
                .when()
                .put("/api/v1/users/me/timezone")
                .then()
                .statusCode(400);
    }

    @Test
    @DisplayName("인증 없이 PUT 호출 시 401")
    void unauthenticated_rejected() {
        RestAssured.given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "timezone": "Asia/Seoul"
                        }
                        """)
                .when()
                .put("/api/v1/users/me/timezone")
                .then()
                .statusCode(401);
    }

    @Test
    @DisplayName("활성 보호자가 시니어의 타임존을 PUT 하면 200 + DB 반영")
    void caregiver_updates_senior_timezone_success() {
        // given
        final SignupResult senior = signup("시니어D");
        final SignupResult caregiver = signup("보호자D");
        careRelationRepository.save(CareRelation.createLinked(senior.userId(), caregiver.userId()));

        // when & then
        RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + caregiver.accessToken())
                .body("""
                        {
                            "timezone": "Australia/Sydney"
                        }
                        """)
                .when()
                .put("/api/v1/users/" + senior.userId() + "/timezone")
                .then()
                .statusCode(200)
                .body("id", equalTo(senior.userId().intValue()))
                .body("timezone", is("Australia/Sydney"));

        final var reloaded = userRepository.findById(senior.userId()).orElseThrow();
        assertThat(reloaded.getTimezone()).isEqualTo("Australia/Sydney");
    }

    @Test
    @DisplayName("관계 없는 사용자가 시니어 타임존 변경 시도하면 403 CARE_001")
    void unrelated_user_rejected() {
        // given
        final SignupResult senior = signup("시니어E");
        final SignupResult stranger = signup("타인E");

        // when & then
        RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + stranger.accessToken())
                .body("""
                        {
                            "timezone": "Asia/Tokyo"
                        }
                        """)
                .when()
                .put("/api/v1/users/" + senior.userId() + "/timezone")
                .then()
                .statusCode(403)
                .body("error.code", is("CARE_001"));
    }

    @Test
    @DisplayName("seniorId 미존재 시 404 USER_001")
    void senior_not_found() {
        // given
        final SignupResult caregiver = signup("보호자F");

        // when & then
        RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + caregiver.accessToken())
                .body("""
                        {
                            "timezone": "Asia/Tokyo"
                        }
                        """)
                .when()
                .put("/api/v1/users/9999999/timezone")
                .then()
                .statusCode(404)
                .body("error.code", is("USER_001"));
    }

    private SignupResult signup(final String nickname) {
        final String loginId = "timezone" + userSequence++;
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
