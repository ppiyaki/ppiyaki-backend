package com.ppiyaki.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import com.ppiyaki.user.domain.CareRelation;
import com.ppiyaki.user.domain.Gender;
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
@DisplayName("PUT /api/v1/users/me, PUT /api/v1/users/{seniorId} E2E")
class ProfileControllerE2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CareRelationRepository careRelationRepository;

    private static long userSequence = 900000L;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    @DisplayName("본인이 이름+기본 프사 인덱스+성별을 PUT 하면 200 + 응답/DB 반영")
    void update_my_profile_with_default_image_success() {
        // given
        final SignupResult user = signup("시니어A");

        // when & then
        RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + user.accessToken())
                .body("""
                        {
                            "nickname": "새이름",
                            "profileImage": 4,
                            "gender": "FEMALE"
                        }
                        """)
                .when()
                .put("/api/v1/users/me")
                .then()
                .statusCode(200)
                .body("id", equalTo(user.userId().intValue()))
                .body("nickname", is("새이름"))
                .body("profileImage", is(4))
                .body("gender", is("FEMALE"));

        final var reloaded = userRepository.findById(user.userId()).orElseThrow();
        assertThat(reloaded.getNickname()).isEqualTo("새이름");
        assertThat(reloaded.getProfileImage()).isEqualTo(4);
        assertThat(reloaded.getProfileImageObjectKey()).isNull();
        assertThat(reloaded.getGender()).isEqualTo(Gender.FEMALE);
    }

    @Test
    @DisplayName("본인이 직접 업로드한 사진 objectKey를 PUT 하면 200 + DB에 objectKey 저장")
    void update_my_profile_with_custom_image_success() {
        // given
        final SignupResult user = signup("시니어B");
        final String objectKey = "profile-image/" + user.userId()
                + "/550e8400-e29b-41d4-a716-446655440000.jpg";

        // when & then
        RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + user.accessToken())
                .body("""
                        {
                            "nickname": "업로더",
                            "profileImageObjectKey": "%s"
                        }
                        """.formatted(objectKey))
                .when()
                .put("/api/v1/users/me")
                .then()
                .statusCode(200)
                .body("id", equalTo(user.userId().intValue()))
                .body("nickname", is("업로더"));

        final var reloaded = userRepository.findById(user.userId()).orElseThrow();
        assertThat(reloaded.getProfileImageObjectKey()).isEqualTo(objectKey);
        assertThat(reloaded.getProfileImage()).isNull();
    }

    @Test
    @DisplayName("기본 프사 인덱스와 커스텀 objectKey를 동시에 보내면 400")
    void both_image_fields_rejected() {
        // given
        final SignupResult user = signup("시니어C");
        final String objectKey = "profile-image/" + user.userId()
                + "/550e8400-e29b-41d4-a716-446655440000.jpg";

        // when & then
        RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + user.accessToken())
                .body("""
                        {
                            "nickname": "둘다",
                            "profileImage": 2,
                            "profileImageObjectKey": "%s"
                        }
                        """.formatted(objectKey))
                .when()
                .put("/api/v1/users/me")
                .then()
                .statusCode(400);
    }

    @Test
    @DisplayName("프로필 인덱스 범위(1~6) 밖이면 400")
    void profile_image_out_of_range_rejected() {
        // given
        final SignupResult user = signup("시니어D");

        // when & then
        RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + user.accessToken())
                .body("""
                        {
                            "nickname": "범위밖",
                            "profileImage": 7
                        }
                        """)
                .when()
                .put("/api/v1/users/me")
                .then()
                .statusCode(400);
    }

    @Test
    @DisplayName("다른 사용자 소유의 objectKey를 보내면 400 COMMON_001")
    void object_key_owner_mismatch_rejected() {
        // given
        final SignupResult user = signup("시니어E");
        final String foreignObjectKey = "profile-image/123456789"
                + "/550e8400-e29b-41d4-a716-446655440000.jpg";

        // when & then
        RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + user.accessToken())
                .body("""
                        {
                            "nickname": "탈취시도",
                            "profileImageObjectKey": "%s"
                        }
                        """.formatted(foreignObjectKey))
                .when()
                .put("/api/v1/users/me")
                .then()
                .statusCode(400)
                .body("error.code", is("COMMON_001"));
    }

    @Test
    @DisplayName("이름 누락 시 400")
    void nickname_missing_rejected() {
        // given
        final SignupResult user = signup("시니어F");

        // when & then
        RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + user.accessToken())
                .body("""
                        {
                            "profileImage": 1
                        }
                        """)
                .when()
                .put("/api/v1/users/me")
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
                            "nickname": "익명",
                            "profileImage": 1
                        }
                        """)
                .when()
                .put("/api/v1/users/me")
                .then()
                .statusCode(401);
    }

    @Test
    @DisplayName("활성 보호자가 시니어의 이름+성별을 PUT 하면 200 + DB 반영")
    void caregiver_updates_senior_profile_success() {
        // given
        final SignupResult senior = signup("시니어G");
        final SignupResult caregiver = signup("보호자G");
        careRelationRepository.save(CareRelation.createLinked(senior.userId(), caregiver.userId()));

        // when & then
        RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + caregiver.accessToken())
                .body("""
                        {
                            "nickname": "할머니",
                            "gender": "FEMALE"
                        }
                        """)
                .when()
                .put("/api/v1/users/" + senior.userId())
                .then()
                .statusCode(200)
                .body("id", equalTo(senior.userId().intValue()))
                .body("nickname", is("할머니"))
                .body("gender", is("FEMALE"));

        final var reloaded = userRepository.findById(senior.userId()).orElseThrow();
        assertThat(reloaded.getNickname()).isEqualTo("할머니");
        assertThat(reloaded.getGender()).isEqualTo(Gender.FEMALE);
    }

    @Test
    @DisplayName("관계 없는 사용자가 시니어 정보 변경 시도하면 403 CARE_001")
    void unrelated_user_rejected() {
        // given
        final SignupResult senior = signup("시니어H");
        final SignupResult stranger = signup("타인H");

        // when & then
        RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + stranger.accessToken())
                .body("""
                        {
                            "nickname": "남의정보",
                            "gender": "MALE"
                        }
                        """)
                .when()
                .put("/api/v1/users/" + senior.userId())
                .then()
                .statusCode(403)
                .body("error.code", is("CARE_001"));
    }

    @Test
    @DisplayName("seniorId 미존재 시 404 USER_001")
    void senior_not_found() {
        // given
        final SignupResult caregiver = signup("보호자I");

        // when & then
        RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + caregiver.accessToken())
                .body("""
                        {
                            "nickname": "없는시니어",
                            "gender": "MALE"
                        }
                        """)
                .when()
                .put("/api/v1/users/9999999")
                .then()
                .statusCode(404)
                .body("error.code", is("USER_001"));
    }

    private SignupResult signup(final String nickname) {
        final String loginId = "profile" + userSequence++;
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
