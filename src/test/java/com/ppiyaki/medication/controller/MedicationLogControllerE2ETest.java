package com.ppiyaki.medication.controller;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import com.ppiyaki.medication.MedicationSchedule;
import com.ppiyaki.medication.repository.MedicationScheduleRepository;
import com.ppiyaki.medicine.Medicine;
import com.ppiyaki.medicine.repository.MedicineRepository;
import com.ppiyaki.user.CareRelation;
import com.ppiyaki.user.repository.CareRelationRepository;
import com.ppiyaki.user.repository.UserRepository;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "ncp.storage.endpoint=https://kr.object.ncloudstorage.com",
        "ncp.storage.region=kr-standard",
        "ncp.storage.access-key=test-access-key",
        "ncp.storage.secret-key=test-secret-key",
        "ncp.storage.bucket-name=ppiyaki-test"
})
@DisplayName("PUT/GET /api/v1/medication-logs E2E")
class MedicationLogControllerE2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CareRelationRepository careRelationRepository;

    @Autowired
    private MedicineRepository medicineRepository;

    @Autowired
    private MedicationScheduleRepository medicationScheduleRepository;

    private static long userSequence = 900000L;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    @DisplayName("시니어 본인 PUT 후 GET으로 photoUrl 조립된 응답을 확인")
    void senior_upsert_and_read() throws Exception {
        // given
        final SignupResult senior = signup("시니어A");
        final Long medicineId = seedMedicine(senior.userId());
        final Long scheduleId = seedSchedule(medicineId);
        final String objectKey = "medication-log/" + senior.userId() + "/abc-uuid.jpg";

        // when — PUT
        RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + senior.accessToken())
                .body("""
                        {
                            "scheduleId": %d,
                            "targetDate": "2026-04-18",
                            "takenAt": "2026-04-18T09:00:00",
                            "status": "TAKEN",
                            "photoObjectKey": "%s"
                        }
                        """.formatted(scheduleId, objectKey))
                .when()
                .put("/api/v1/medication-logs")
                .then()
                .statusCode(200)
                .body("status", is("TAKEN"))
                .body("isProxy", is(false))
                .body("confirmedByUserId", equalTo(senior.userId().intValue()))
                .body("photoUrl", is("https://kr.object.ncloudstorage.com/ppiyaki-test/" + objectKey));

        // then — GET
        RestAssured.given()
                .header("Authorization", "Bearer " + senior.accessToken())
                .queryParam("from", "2026-04-18")
                .queryParam("to", "2026-04-18")
                .when()
                .get("/api/v1/medication-logs")
                .then()
                .statusCode(200)
                .body("responses", hasSize(1))
                .body("responses[0].status", is("TAKEN"))
                .body("responses[0].photoUrl", notNullValue());
    }

    @Test
    @DisplayName("동일 (scheduleId, targetDate) 두 번 PUT 시 단일 row 유지 (멱등)")
    void idempotent_put() throws Exception {
        final SignupResult senior = signup("시니어B");
        final Long medicineId = seedMedicine(senior.userId());
        final Long scheduleId = seedSchedule(medicineId);

        for (int i = 0; i < 2; i++) {
            RestAssured.given()
                    .contentType(ContentType.JSON)
                    .header("Authorization", "Bearer " + senior.accessToken())
                    .body("""
                            {"scheduleId": %d, "targetDate": "2026-04-19", "status": "TAKEN"}
                            """.formatted(scheduleId))
                    .when()
                    .put("/api/v1/medication-logs")
                    .then()
                    .statusCode(200);
        }

        RestAssured.given()
                .header("Authorization", "Bearer " + senior.accessToken())
                .queryParam("from", "2026-04-19")
                .queryParam("to", "2026-04-19")
                .when()
                .get("/api/v1/medication-logs")
                .then()
                .statusCode(200)
                .body("responses", hasSize(1));
    }

    @Test
    @DisplayName("보호자 대리 PUT 시 isProxy=true")
    void caregiver_proxy_put() throws Exception {
        final SignupResult senior = signup("시니어C");
        final SignupResult caregiver = signup("보호자C");
        seedCareRelation(senior.userId(), caregiver.userId());
        final Long medicineId = seedMedicine(senior.userId());
        final Long scheduleId = seedSchedule(medicineId);

        RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + caregiver.accessToken())
                .body("""
                        {"scheduleId": %d, "targetDate": "2026-04-20", "status": "TAKEN"}
                        """.formatted(scheduleId))
                .when()
                .put("/api/v1/medication-logs")
                .then()
                .statusCode(200)
                .body("isProxy", is(true))
                .body("confirmedByUserId", equalTo(caregiver.userId().intValue()))
                .body("seniorId", equalTo(senior.userId().intValue()));
    }

    @Test
    @DisplayName("관계 없는 사용자 PUT 시 403 CARE_001")
    void unrelated_user_rejected() throws Exception {
        final SignupResult senior = signup("시니어D");
        final SignupResult stranger = signup("타인D");
        final Long medicineId = seedMedicine(senior.userId());
        final Long scheduleId = seedSchedule(medicineId);

        RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + stranger.accessToken())
                .body("""
                        {"scheduleId": %d, "targetDate": "2026-04-21", "status": "TAKEN"}
                        """.formatted(scheduleId))
                .when()
                .put("/api/v1/medication-logs")
                .then()
                .statusCode(403)
                .body("error.code", is("CARE_001"));
    }

    @Test
    @DisplayName("photoObjectKey의 userId 세그먼트가 요청자와 다르면 400 COMMON_001")
    void photoObjectKey_owner_mismatch() throws Exception {
        final SignupResult senior = signup("시니어E");
        final Long medicineId = seedMedicine(senior.userId());
        final Long scheduleId = seedSchedule(medicineId);

        RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + senior.accessToken())
                .body("""
                        {
                            "scheduleId": %d,
                            "targetDate": "2026-04-22",
                            "status": "TAKEN",
                            "photoObjectKey": "medication-log/9999999/uuid.jpg"
                        }
                        """.formatted(scheduleId))
                .when()
                .put("/api/v1/medication-logs")
                .then()
                .statusCode(400)
                .body("error.code", is("COMMON_001"));
    }

    @Test
    @DisplayName("조회 기간 31일 초과 시 400")
    void query_range_exceeded() throws Exception {
        final SignupResult senior = signup("시니어F");

        RestAssured.given()
                .header("Authorization", "Bearer " + senior.accessToken())
                .queryParam("from", "2026-04-01")
                .queryParam("to", "2026-05-05")
                .when()
                .get("/api/v1/medication-logs")
                .then()
                .statusCode(400);
    }

    private SignupResult signup(final String nickname) {
        final String loginId = "medlog" + userSequence++;
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
        careRelationRepository.save(new CareRelation(seniorId, caregiverId, "INVITE-" + seniorId + "-" + caregiverId));
    }

    private Long seedMedicine(final Long ownerId) {
        return medicineRepository.save(new Medicine(ownerId, null, "테스트약", 30, 30, "ITEM-1", null)).getId();
    }

    private Long seedSchedule(final Long medicineId) throws Exception {
        final Constructor<MedicationSchedule> ctor = MedicationSchedule.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        final MedicationSchedule schedule = ctor.newInstance();
        setField(schedule, "medicineId", medicineId);
        setField(schedule, "scheduledTime", LocalTime.of(9, 0));
        setField(schedule, "dosage", "1정");
        setField(schedule, "daysOfWeek", "DAILY");
        setField(schedule, "startDate", LocalDate.of(2026, 4, 1));
        return medicationScheduleRepository.save(schedule).getId();
    }

    private static void setField(final Object target, final String fieldName, final Object value) throws Exception {
        Class<?> clazz = target.getClass();
        while (clazz != null) {
            try {
                final Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (final NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }

    private record SignupResult(Long userId, String accessToken) {
    }
}
