package com.ppiyaki.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import com.ppiyaki.user.domain.CareRelation;
import com.ppiyaki.user.domain.User;
import com.ppiyaki.user.repository.CareRelationRepository;
import com.ppiyaki.user.repository.UserRepository;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("PUT /api/v1/users/me/meal-times E2E")
class MealTimesControllerE2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CareRelationRepository careRelationRepository;

    private static long userSequence = 800000L;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    @DisplayName("인증된 사용자가 식사 시간 3개를 PUT 하면 200 + 응답에 mealTimes 포함 + DB 반영")
    void update_meal_times_success() {
        // given
        final SignupResult user = signup("시니어A");

        // when & then
        RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + user.accessToken())
                .body("""
                        {
                            "breakfast": "08:00:00",
                            "lunch": "12:30:00",
                            "dinner": "18:30:00"
                        }
                        """)
                .when()
                .put("/api/v1/users/me/meal-times")
                .then()
                .statusCode(200)
                .body("id", equalTo(user.userId().intValue()))
                .body("mealTimes.breakfast", is("08:00:00"))
                .body("mealTimes.lunch", is("12:30:00"))
                .body("mealTimes.dinner", is("18:30:00"));

        final User reloaded = userRepository.findById(user.userId()).orElseThrow();
        assertThat(reloaded.getBreakfastTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(reloaded.getLunchTime()).isEqualTo(LocalTime.of(12, 30));
        assertThat(reloaded.getDinnerTime()).isEqualTo(LocalTime.of(18, 30));
    }

    @Test
    @DisplayName("미설정 사용자가 GET /users/me 호출 시 mealTimes는 null")
    void get_me_returns_null_meal_times_when_unset() {
        // given
        final SignupResult user = signup("시니어B");

        // when & then
        RestAssured.given()
                .header("Authorization", "Bearer " + user.accessToken())
                .when()
                .get("/api/v1/users/me")
                .then()
                .statusCode(200)
                .body("id", equalTo(user.userId().intValue()))
                .body("mealTimes", nullValue());
    }

    @Test
    @DisplayName("breakfast 누락 시 400")
    void breakfast_missing_rejected() {
        // given
        final SignupResult user = signup("시니어C");

        // when & then
        RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + user.accessToken())
                .body("""
                        {
                            "lunch": "12:30:00",
                            "dinner": "18:30:00"
                        }
                        """)
                .when()
                .put("/api/v1/users/me/meal-times")
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
                            "breakfast": "08:00:00",
                            "lunch": "12:30:00",
                            "dinner": "18:30:00"
                        }
                        """)
                .when()
                .put("/api/v1/users/me/meal-times")
                .then()
                .statusCode(401);
    }

    @Test
    @DisplayName("활성 보호자가 시니어 mealTimes 변경 시 200 + DB 반영")
    void caregiver_updates_senior_meal_times_success() {
        // given
        final SignupResult senior = signup("시니어E");
        final SignupResult caregiver = signup("보호자E");
        careRelationRepository.save(CareRelation.createLinked(senior.userId(), caregiver.userId()));

        // when & then
        RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + caregiver.accessToken())
                .body("""
                        {
                            "breakfast": "07:30:00",
                            "lunch": "12:00:00",
                            "dinner": "19:00:00"
                        }
                        """)
                .when()
                .put("/api/v1/users/" + senior.userId() + "/meal-times")
                .then()
                .statusCode(200)
                .body("id", equalTo(senior.userId().intValue()))
                .body("mealTimes.breakfast", is("07:30:00"))
                .body("mealTimes.lunch", is("12:00:00"))
                .body("mealTimes.dinner", is("19:00:00"));

        final User reloaded = userRepository.findById(senior.userId()).orElseThrow();
        assertThat(reloaded.getBreakfastTime()).isEqualTo(LocalTime.of(7, 30));
        assertThat(reloaded.getLunchTime()).isEqualTo(LocalTime.of(12, 0));
        assertThat(reloaded.getDinnerTime()).isEqualTo(LocalTime.of(19, 0));
    }

    @Test
    @DisplayName("관계 없는 사용자가 시니어 mealTimes 변경 시도하면 403 CARE_001")
    void unrelated_user_rejected_when_updating_senior_meal_times() {
        // given
        final SignupResult senior = signup("시니어F");
        final SignupResult stranger = signup("타인G");

        // when & then
        RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + stranger.accessToken())
                .body("""
                        {
                            "breakfast": "08:00:00",
                            "lunch": "12:30:00",
                            "dinner": "18:30:00"
                        }
                        """)
                .when()
                .put("/api/v1/users/" + senior.userId() + "/meal-times")
                .then()
                .statusCode(403)
                .body("error.code", is("CARE_001"));
    }

    @Test
    @DisplayName("seniorId 미존재 시 404 USER_001")
    void senior_not_found_when_updating_meal_times() {
        // given
        final SignupResult caregiver = signup("보호자H");

        // when & then
        RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + caregiver.accessToken())
                .body("""
                        {
                            "breakfast": "08:00:00",
                            "lunch": "12:30:00",
                            "dinner": "18:30:00"
                        }
                        """)
                .when()
                .put("/api/v1/users/9999999/meal-times")
                .then()
                .statusCode(404)
                .body("error.code", is("USER_001"));
    }

    private SignupResult signup(final String nickname) {
        final String loginId = "mealtime" + userSequence++;
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
