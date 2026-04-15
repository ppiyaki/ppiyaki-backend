package com.ppiyaki.medication.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import com.ppiyaki.medicine.Medicine;
import com.ppiyaki.medicine.repository.MedicineRepository;
import com.ppiyaki.user.CareRelation;
import com.ppiyaki.user.repository.CareRelationRepository;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MedicationScheduleControllerE2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private MedicineRepository medicineRepository;

    @Autowired
    private CareRelationRepository careRelationRepository;

    private static long userSequence = 400000L;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    @DisplayName("복약 일정 등록 시 201 응답과 생성된 일정 정보를 반환한다")
    void create_success() {
        // given
        final String token = loginAsNewUser("일정등록유저");
        final Integer medicineId = createMedicine(token, "타이레놀정", 30, 25);

        // when & then
        RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + token)
                .body("""
                        {
                            "scheduledTime": "08:00",
                            "dosage": "1정",
                            "daysOfWeek": "DAILY",
                            "startDate": "2026-04-11"
                        }
                        """)
                .when()
                .post("/api/v1/medicines/" + medicineId + "/schedules")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("medicineId", is(medicineId))
                .body("scheduledTime", is("08:00:00"))
                .body("dosage", is("1정"))
                .body("daysOfWeek", is("DAILY"));
    }

    @Test
    @DisplayName("약물의 복약 일정 목록을 조회한다")
    void readAll_success() {
        // given
        final String token = loginAsNewUser("목록유저");
        final Integer medicineId = createMedicine(token, "비타민C", 60, 50);
        createSchedule(token, medicineId, "08:00", "1정");
        createSchedule(token, medicineId, "20:00", "1정");

        // when & then
        RestAssured.given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/medicines/" + medicineId + "/schedules")
                .then()
                .statusCode(200)
                .body("responses", hasSize(2));
    }

    @Test
    @DisplayName("복약 일정 상세 조회에 성공한다")
    void readById_success() {
        // given
        final String token = loginAsNewUser("상세유저");
        final Integer medicineId = createMedicine(token, "오메가3", 90, 80);
        final Integer scheduleId = createSchedule(token, medicineId, "09:00", "2정");

        // when & then
        RestAssured.given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/medicines/" + medicineId + "/schedules/" + scheduleId)
                .then()
                .statusCode(200)
                .body("dosage", is("2정"))
                .body("scheduledTime", is("09:00:00"));
    }

    @Test
    @DisplayName("복약 일정을 수정한다")
    void update_success() {
        // given
        final String token = loginAsNewUser("수정유저");
        final Integer medicineId = createMedicine(token, "아스피린", 20, 15);
        final Integer scheduleId = createSchedule(token, medicineId, "08:00", "1정");

        // when & then
        RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + token)
                .body("""
                        {
                            "scheduledTime": "09:30",
                            "dosage": "2정"
                        }
                        """)
                .when()
                .patch("/api/v1/medicines/" + medicineId + "/schedules/" + scheduleId)
                .then()
                .statusCode(200)
                .body("scheduledTime", is("09:30:00"))
                .body("dosage", is("2정"));
    }

    @Test
    @DisplayName("복약 일정을 삭제한다")
    void delete_success() {
        // given
        final String token = loginAsNewUser("삭제유저");
        final Integer medicineId = createMedicine(token, "삭제약", 10, 5);
        final Integer scheduleId = createSchedule(token, medicineId, "12:00", "1정");

        // when & then
        RestAssured.given()
                .header("Authorization", "Bearer " + token)
                .when()
                .delete("/api/v1/medicines/" + medicineId + "/schedules/" + scheduleId)
                .then()
                .statusCode(204);
    }

    @Test
    @DisplayName("연동된 보호자는 시니어 약물에 대한 일정 등록/조회에 성공한다")
    void caregiver_canAccessSeniorSchedules() {
        // given
        final String seniorToken = loginAsNewUser("시니어");
        final Long seniorUserId = readUserId(seniorToken);
        final Integer medicineId = createMedicine(seniorToken, "시니어약", 30, 20);

        final String caregiverToken = loginAsNewUser("보호자");
        final Long caregiverUserId = readUserId(caregiverToken);

        careRelationRepository.save(new CareRelation(seniorUserId, caregiverUserId, "INVITE"));

        // when & then: caregiver create
        final Integer scheduleId = RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + caregiverToken)
                .body("""
                        {
                            "scheduledTime": "07:30",
                            "dosage": "1정",
                            "daysOfWeek": "DAILY"
                        }
                        """)
                .when()
                .post("/api/v1/medicines/" + medicineId + "/schedules")
                .then()
                .statusCode(201)
                .body("medicineId", is(medicineId))
                .extract()
                .path("id");

        // and: caregiver can read it back
        RestAssured.given()
                .header("Authorization", "Bearer " + caregiverToken)
                .when()
                .get("/api/v1/medicines/" + medicineId + "/schedules/" + scheduleId)
                .then()
                .statusCode(200)
                .body("dosage", is("1정"));
    }

    @Test
    @DisplayName("다른 사용자의 약물에 일정 등록 시 403 응답")
    void create_forbidden() {
        // given
        final String ownerToken = loginAsNewUser("소유자");
        final Integer medicineId = createMedicine(ownerToken, "소유자약", 10, 5);

        final String otherToken = loginAsNewUser("다른유저");

        // when & then
        RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + otherToken)
                .body("""
                        {
                            "scheduledTime": "08:00",
                            "dosage": "1정"
                        }
                        """)
                .when()
                .post("/api/v1/medicines/" + medicineId + "/schedules")
                .then()
                .statusCode(403);
    }

    private String loginAsNewUser(final String nickname) {
        final String loginId = "scheduletest" + userSequence++;
        return RestAssured.given()
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
    }

    private Integer createMedicine(final String token, final String name,
            final int totalAmount, final int remainingAmount) {
        final Long userId = readUserId(token);

        final Medicine medicine = medicineRepository.save(
                new Medicine(userId, null, name, totalAmount, remainingAmount, null));
        return medicine.getId().intValue();
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

    private Integer createSchedule(
            final String token,
            final Integer medicineId,
            final String time,
            final String dosage
    ) {
        return RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + token)
                .body("""
                        {
                            "scheduledTime": "%s",
                            "dosage": "%s"
                        }
                        """.formatted(time, dosage))
                .when()
                .post("/api/v1/medicines/" + medicineId + "/schedules")
                .then()
                .extract()
                .path("id");
    }
}
