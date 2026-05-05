package com.ppiyaki.medicine.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import com.ppiyaki.medication.MedicationSchedule;
import com.ppiyaki.medication.repository.MedicationScheduleRepository;
import com.ppiyaki.user.CareRelation;
import com.ppiyaki.user.repository.CareRelationRepository;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MedicineControllerE2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private MedicationScheduleRepository medicationScheduleRepository;

    @Autowired
    private CareRelationRepository careRelationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static long userSequence = 200000L;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    private String loginAsNewUser(final String nickname) {
        final String loginId = "testuser" + userSequence++;
        final String token = RestAssured.given()
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
                .extract()
                .path("accessToken");
        // 회원가입 시 CAREGIVER로 생성되므로 시니어 테스트를 위해 role 변경
        jdbcTemplate.update("UPDATE users SET role = 'SENIOR' WHERE login_id = ?", loginId);
        return token;
    }

    @Test
    @DisplayName("약물 등록 시 201 응답과 생성된 약물 정보를 반환한다")
    void create_success() {
        // given
        final String token = loginAsNewUser("등록유저");

        // when & then
        RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + token)
                .body("""
                        {
                            "name": "타이레놀정",
                            "totalAmount": 30,
                            "remainingAmount": 25,
                            "durWarningText": "공복 복용 시 위장장애 주의"
                        }
                        """)
                .when()
                .post("/api/v1/medicines")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("name", is("타이레놀정"))
                .body("totalAmount", is(30))
                .body("remainingAmount", is(25))
                .body("durWarningText", is("공복 복용 시 위장장애 주의"));
    }

    @Test
    @DisplayName("본인 약물 목록을 조회한다")
    void readAll_success() {
        // given
        final String token = loginAsNewUser("목록유저");
        createMedicine(token, "타이레놀정", 30, 25);
        createMedicine(token, "비타민C", 60, 50);

        // when & then
        RestAssured.given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/medicines")
                .then()
                .statusCode(200)
                .body("responses", hasSize(2));
    }

    @Test
    @DisplayName("약물 상세 조회에 성공한다")
    void readById_success() {
        // given
        final String token = loginAsNewUser("상세유저");
        final Integer medicineId = createMedicine(token, "오메가3", 90, 80);

        // when & then
        RestAssured.given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/medicines/" + medicineId)
                .then()
                .statusCode(200)
                .body("name", is("오메가3"))
                .body("totalAmount", is(90));
    }

    @Test
    @DisplayName("약물 정보를 수정한다")
    void update_success() {
        // given
        final String token = loginAsNewUser("수정유저");
        final Integer medicineId = createMedicine(token, "아스피린", 20, 15);

        // when & then
        RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + token)
                .body("""
                        {
                            "remainingAmount": 10
                        }
                        """)
                .when()
                .patch("/api/v1/medicines/" + medicineId)
                .then()
                .statusCode(200)
                .body("name", is("아스피린"))
                .body("remainingAmount", is(10));
    }

    @Test
    @DisplayName("약물을 삭제한다")
    void delete_success() {
        // given
        final String token = loginAsNewUser("삭제유저");
        final Integer medicineId = createMedicine(token, "삭제대상약", 10, 5);

        // when & then
        RestAssured.given()
                .header("Authorization", "Bearer " + token)
                .when()
                .delete("/api/v1/medicines/" + medicineId)
                .then()
                .statusCode(200)
                .body("deletedMedicineId", is(medicineId))
                .body("deletedScheduleCount", is(0));
    }

    @Test
    @DisplayName("약물 삭제 시 연관 복약 일정도 cascade 삭제된다")
    void delete_cascadeSchedules() {
        // given
        final String token = loginAsNewUser("캐스케이드유저");
        final Integer medicineId = createMedicine(token, "캐스케이드약", 30, 20);

        medicationScheduleRepository.save(
                new MedicationSchedule(Long.valueOf(medicineId), LocalTime.of(8, 0),
                        "1정", "DAILY", LocalDate.now(), null));
        medicationScheduleRepository.save(
                new MedicationSchedule(Long.valueOf(medicineId), LocalTime.of(20, 0),
                        "1정", "DAILY", LocalDate.now(), null));

        // when & then
        RestAssured.given()
                .header("Authorization", "Bearer " + token)
                .when()
                .delete("/api/v1/medicines/" + medicineId)
                .then()
                .statusCode(200)
                .body("deletedMedicineId", is(medicineId))
                .body("deletedScheduleCount", is(2));
    }

    @Test
    @DisplayName("인증 없이 약물 API 호출 시 401 응답")
    void unauthorized() {
        // when & then
        RestAssured.given()
                .when()
                .get("/api/v1/medicines")
                .then()
                .statusCode(401);
    }

    @Test
    @DisplayName("연동된 보호자는 시니어 약물을 seniorId 지정으로 등록/조회할 수 있다")
    void caregiver_linked_canManageSeniorMedicines() {
        // given
        final String seniorToken = loginAsNewUser("시니어연동");
        final Long seniorUserId = readUserId(seniorToken);

        final String caregiverToken = loginAsNewUser("보호자연동");
        final Long caregiverUserId = readUserId(caregiverToken);
        setUserRoleToCaregiver(caregiverUserId);

        careRelationRepository.save(new CareRelation(seniorUserId, caregiverUserId, "INVITE-OK"));

        // when & then: caregiver creates medicine for senior
        RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + caregiverToken)
                .body("""
                        {
                            "seniorId": %d,
                            "name": "시니어약",
                            "totalAmount": 30,
                            "remainingAmount": 25
                        }
                        """.formatted(seniorUserId))
                .when()
                .post("/api/v1/medicines")
                .then()
                .statusCode(201)
                .body("name", is("시니어약"));

        // and: caregiver can read senior's medicine list
        RestAssured.given()
                .header("Authorization", "Bearer " + caregiverToken)
                .when()
                .get("/api/v1/medicines?seniorId=" + seniorUserId)
                .then()
                .statusCode(200)
                .body("responses", hasSize(1));
    }

    @Test
    @DisplayName("미연동 보호자가 시니어 약물에 접근 시 403 응답")
    void caregiver_notLinked_isForbidden() {
        // given
        final String seniorToken = loginAsNewUser("시니어미연동");
        final Long seniorUserId = readUserId(seniorToken);

        final String caregiverToken = loginAsNewUser("보호자미연동");
        final Long caregiverUserId = readUserId(caregiverToken);
        setUserRoleToCaregiver(caregiverUserId);

        // when & then: create 시도 → 403
        RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + caregiverToken)
                .body("""
                        {
                            "seniorId": %d,
                            "name": "거부약",
                            "totalAmount": 10,
                            "remainingAmount": 10
                        }
                        """.formatted(seniorUserId))
                .when()
                .post("/api/v1/medicines")
                .then()
                .statusCode(403);

        // and: list 조회도 403
        RestAssured.given()
                .header("Authorization", "Bearer " + caregiverToken)
                .when()
                .get("/api/v1/medicines?seniorId=" + seniorUserId)
                .then()
                .statusCode(403);
    }

    private Long readUserId(final String token) {
        final Integer userId = RestAssured.given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/users/me")
                .then()
                .extract()
                .path("id");
        return Long.valueOf(userId);
    }

    private void setUserRoleToCaregiver(final Long userId) {
        jdbcTemplate.update("UPDATE users SET role = ? WHERE id = ?", "CAREGIVER", userId);
    }

    private Integer createMedicine(
            final String token,
            final String name,
            final int totalAmount,
            final int remainingAmount
    ) {
        return RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + token)
                .body("""
                        {
                            "name": "%s",
                            "totalAmount": %d,
                            "remainingAmount": %d
                        }
                        """.formatted(name, totalAmount, remainingAmount))
                .when()
                .post("/api/v1/medicines")
                .then()
                .extract()
                .path("id");
    }

}
