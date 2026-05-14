package com.ppiyaki.user.controller;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import com.ppiyaki.user.domain.CareMode;
import com.ppiyaki.user.domain.CareRelation;
import com.ppiyaki.user.domain.User;
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
@DisplayName("PUT /api/v1/users/{seniorId}/care-mode E2E")
class CareModeControllerE2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CareRelationRepository careRelationRepository;

    private static long userSequence = 700000L;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    @DisplayName("활성 보호자가 시니어 careMode를 AUTONOMOUS로 변경하면 200 + 새 모드 반환")
    void caregiver_updates_senior_careMode_success() {
        // given
        final SignupResult senior = signup("시니어A");
        final SignupResult caregiver = signup("보호자A");
        seedCareRelation(senior.userId(), caregiver.userId());

        // when & then
        RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + caregiver.accessToken())
                .body("""
                        {"careMode": "AUTONOMOUS"}
                        """)
                .when()
                .put("/api/v1/users/" + senior.userId() + "/care-mode")
                .then()
                .statusCode(200)
                .body("userId", equalTo(senior.userId().intValue()))
                .body("careMode", is("AUTONOMOUS"));

        final User updated = userRepository.findById(senior.userId()).orElseThrow();
        assert updated.getCareMode() == CareMode.AUTONOMOUS;
    }

    @Test
    @DisplayName("시니어 본인이 자기 careMode 변경 시도하면 403 CARE_001")
    void senior_self_update_rejected() {
        // given
        final SignupResult senior = signup("시니어B");

        // when & then
        RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + senior.accessToken())
                .body("""
                        {"careMode": "AUTONOMOUS"}
                        """)
                .when()
                .put("/api/v1/users/" + senior.userId() + "/care-mode")
                .then()
                .statusCode(403)
                .body("error.code", is("CARE_001"));
    }

    @Test
    @DisplayName("관계 없는 사용자가 변경 시도하면 403 CARE_001")
    void unrelated_user_rejected() {
        // given
        final SignupResult senior = signup("시니어C");
        final SignupResult stranger = signup("타인D");

        // when & then
        RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + stranger.accessToken())
                .body("""
                        {"careMode": "AUTONOMOUS"}
                        """)
                .when()
                .put("/api/v1/users/" + senior.userId() + "/care-mode")
                .then()
                .statusCode(403)
                .body("error.code", is("CARE_001"));
    }

    @Test
    @DisplayName("seniorId 미존재 시 404 USER_001")
    void senior_not_found() {
        // given
        final SignupResult caregiver = signup("보호자E");

        // when & then
        RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + caregiver.accessToken())
                .body("""
                        {"careMode": "AUTONOMOUS"}
                        """)
                .when()
                .put("/api/v1/users/9999999/care-mode")
                .then()
                .statusCode(404)
                .body("error.code", is("USER_001"));
    }

    @Test
    @DisplayName("careMode가 null이면 400 COMMON_001")
    void invalid_payload_rejected() {
        // given
        final SignupResult caregiver = signup("보호자F");

        // when & then
        RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + caregiver.accessToken())
                .body("""
                        {"careMode": null}
                        """)
                .when()
                .put("/api/v1/users/" + caregiver.userId() + "/care-mode")
                .then()
                .statusCode(400);
    }

    private SignupResult signup(final String nickname) {
        final String loginId = "caremode" + userSequence++;
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

    private void seedCareRelation(final Long seniorId, final Long caregiverId) {
        careRelationRepository.save(CareRelation.createLinked(seniorId, caregiverId));
    }

    private record SignupResult(Long userId, String accessToken) {
    }
}
