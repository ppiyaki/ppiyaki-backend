package com.ppiyaki.prescription.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;

import com.ppiyaki.medication.MealSlot;
import com.ppiyaki.medication.MedicationSchedule;
import com.ppiyaki.medication.repository.MedicationScheduleRepository;
import com.ppiyaki.medicine.Medicine;
import com.ppiyaki.medicine.repository.MedicineRepository;
import com.ppiyaki.medicine.service.MatchType;
import com.ppiyaki.prescription.Prescription;
import com.ppiyaki.prescription.PrescriptionMedicineCandidate;
import com.ppiyaki.prescription.PrescriptionStatus;
import com.ppiyaki.prescription.repository.PrescriptionMedicineCandidateRepository;
import com.ppiyaki.prescription.repository.PrescriptionRepository;
import com.ppiyaki.user.CareMode;
import com.ppiyaki.user.User;
import com.ppiyaki.user.repository.UserRepository;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import java.lang.reflect.Field;
import java.time.LocalTime;
import java.util.List;
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
@DisplayName("POST /prescriptions/{id}/confirm 시 슬롯별 schedule 자동 생성 E2E")
class PrescriptionConfirmAutoScheduleE2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PrescriptionRepository prescriptionRepository;

    @Autowired
    private PrescriptionMedicineCandidateRepository candidateRepository;

    @Autowired
    private MedicineRepository medicineRepository;

    @Autowired
    private MedicationScheduleRepository medicationScheduleRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private static long userSequence = 600000L;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    @DisplayName("ACCEPTED + confirmedMealSlots=[BREAKFAST,LUNCH,DINNER] → schedule 3건 생성")
    void confirm_createsSchedulesForEachSlot() {
        // given
        final SignupResult senior = signup("정상시니어");
        setSeniorMode(senior.userId(), CareMode.AUTONOMOUS);
        setMealTimes(senior.userId(),
                LocalTime.of(8, 0), LocalTime.of(12, 30), LocalTime.of(18, 30));
        final Long prescriptionId = seedPrescription(senior.userId());
        final Long candidateId = seedAcceptedCandidate(prescriptionId, "1정",
                List.of(MealSlot.BREAKFAST, MealSlot.LUNCH, MealSlot.DINNER));

        // when
        RestAssured.given()
                .header("Authorization", "Bearer " + senior.accessToken())
                .when()
                .post("/api/v1/prescriptions/" + prescriptionId + "/confirm")
                .then()
                .statusCode(200)
                .body("status", is("CONFIRMED"));

        // then — Medicine 1건 + schedule 3건
        final List<Medicine> medicines = medicineRepository.findByOwnerId(senior.userId());
        assertThat(medicines).hasSize(1);
        final Long medicineId = medicines.get(0).getId();

        final List<MedicationSchedule> schedules = medicationScheduleRepository.findByMedicineId(medicineId);
        assertThat(schedules).extracting(MedicationSchedule::getMealSlot)
                .containsExactlyInAnyOrder(MealSlot.BREAKFAST, MealSlot.LUNCH, MealSlot.DINNER);
        assertThat(schedules).allMatch(s -> "1정".equals(s.getDosage()));
        assertThat(schedules).allMatch(s -> "DAILY".equals(s.getDaysOfWeek()));

        // candidate에 created_medicine_id 채워짐
        final PrescriptionMedicineCandidate after = candidateRepository.findById(candidateId).orElseThrow();
        assertThat(after.getCreatedMedicineId()).isEqualTo(medicineId);
    }

    @Test
    @DisplayName("confirmedMealSlots에 mealTime 미설정 슬롯 포함 → 400 USER_002, 트랜잭션 롤백")
    void confirm_mealTimesNotSet_returns400_andRollsBack() {
        // given — lunchTime만 null
        final SignupResult senior = signup("미설정시니어");
        setSeniorMode(senior.userId(), CareMode.AUTONOMOUS);
        setMealTimes(senior.userId(), LocalTime.of(8, 0), null, LocalTime.of(18, 30));
        final Long prescriptionId = seedPrescription(senior.userId());
        seedAcceptedCandidate(prescriptionId, "1정",
                List.of(MealSlot.LUNCH)); // lunchTime null이라 거절돼야 함

        // when
        RestAssured.given()
                .header("Authorization", "Bearer " + senior.accessToken())
                .when()
                .post("/api/v1/prescriptions/" + prescriptionId + "/confirm")
                .then()
                .statusCode(400)
                .body("error.code", is("USER_002"));

        // then — Medicine/Schedule 모두 생성되지 않음, prescription status도 CONFIRMED 아님
        assertThat(medicineRepository.findByOwnerId(senior.userId())).isEmpty();
        final Prescription after = prescriptionRepository.findById(prescriptionId).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(PrescriptionStatus.PENDING_REVIEW);
    }

    @Test
    @DisplayName("dosage가 null이면 schedule skip — Medicine만 생성")
    void confirm_dosageNull_skipsScheduleCreation() {
        // given
        final SignupResult senior = signup("도세지없음시니어");
        setSeniorMode(senior.userId(), CareMode.AUTONOMOUS);
        setMealTimes(senior.userId(),
                LocalTime.of(8, 0), LocalTime.of(12, 30), LocalTime.of(18, 30));
        final Long prescriptionId = seedPrescription(senior.userId());
        seedAcceptedCandidate(prescriptionId, null,
                List.of(MealSlot.BREAKFAST));

        // when
        RestAssured.given()
                .header("Authorization", "Bearer " + senior.accessToken())
                .when()
                .post("/api/v1/prescriptions/" + prescriptionId + "/confirm")
                .then()
                .statusCode(200);

        // then — Medicine 1건, schedule 0건
        final List<Medicine> medicines = medicineRepository.findByOwnerId(senior.userId());
        assertThat(medicines).hasSize(1);
        assertThat(medicationScheduleRepository.findByMedicineId(medicines.get(0).getId())).isEmpty();
    }

    @Test
    @DisplayName("confirmedMealSlots empty면 Medicine만 생성, schedule 0건")
    void confirm_emptyMealSlots_onlyMedicine() {
        // given
        final SignupResult senior = signup("슬롯없음시니어");
        setSeniorMode(senior.userId(), CareMode.AUTONOMOUS);
        setMealTimes(senior.userId(),
                LocalTime.of(8, 0), LocalTime.of(12, 30), LocalTime.of(18, 30));
        final Long prescriptionId = seedPrescription(senior.userId());
        seedAcceptedCandidate(prescriptionId, "1정", List.of());

        // when
        RestAssured.given()
                .header("Authorization", "Bearer " + senior.accessToken())
                .when()
                .post("/api/v1/prescriptions/" + prescriptionId + "/confirm")
                .then()
                .statusCode(200);

        // then — Medicine 1건, schedule 0건
        final List<Medicine> medicines = medicineRepository.findByOwnerId(senior.userId());
        assertThat(medicines).hasSize(1);
        assertThat(medicationScheduleRepository.findByMedicineId(medicines.get(0).getId())).isEmpty();
    }

    @Test
    @DisplayName("confirm 두 번째 호출은 멱등 — Medicine/schedule 중복 생성 안 됨")
    void confirm_idempotent_secondCallNoOp() {
        // given
        final SignupResult senior = signup("멱등시니어");
        setSeniorMode(senior.userId(), CareMode.AUTONOMOUS);
        setMealTimes(senior.userId(),
                LocalTime.of(8, 0), LocalTime.of(12, 30), LocalTime.of(18, 30));
        final Long prescriptionId = seedPrescription(senior.userId());
        seedAcceptedCandidate(prescriptionId, "1정",
                List.of(MealSlot.BREAKFAST, MealSlot.DINNER));

        // when — 1차 confirm
        RestAssured.given()
                .header("Authorization", "Bearer " + senior.accessToken())
                .when()
                .post("/api/v1/prescriptions/" + prescriptionId + "/confirm")
                .then()
                .statusCode(200);
        final List<Medicine> afterFirst = medicineRepository.findByOwnerId(senior.userId());
        assertThat(afterFirst).hasSize(1);
        final Long medicineId = afterFirst.get(0).getId();
        assertThat(medicationScheduleRepository.findByMedicineId(medicineId)).hasSize(2);

        // when — 2차 confirm (이미 CONFIRMED라 동일 호출)
        RestAssured.given()
                .header("Authorization", "Bearer " + senior.accessToken())
                .when()
                .post("/api/v1/prescriptions/" + prescriptionId + "/confirm")
                .then()
                .statusCode(200);

        // then — Medicine·schedule 카운트 그대로
        assertThat(medicineRepository.findByOwnerId(senior.userId())).hasSize(1);
        assertThat(medicationScheduleRepository.findByMedicineId(medicineId)).hasSize(2);
    }

    private SignupResult signup(final String nickname) {
        final String loginId = "presauto" + userSequence++;
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

    private void setSeniorMode(final Long seniorId, final CareMode mode) {
        transactionTemplate.executeWithoutResult(status -> {
            final User user = userRepository.findById(seniorId).orElseThrow();
            user.changeCareMode(mode);
            userRepository.save(user);
        });
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

    private Long seedPrescription(final Long seniorId) {
        return transactionTemplate.execute(status -> {
            final Prescription prescription = new Prescription(seniorId);
            setHierarchicalField(prescription, "status", PrescriptionStatus.PENDING_REVIEW);
            return prescriptionRepository.save(prescription).getId();
        });
    }

    private Long seedAcceptedCandidate(
            final Long prescriptionId,
            final String dosage,
            final List<MealSlot> confirmedSlots
    ) {
        return transactionTemplate.execute(status -> {
            final PrescriptionMedicineCandidate candidate = new PrescriptionMedicineCandidate(
                    prescriptionId, "raw", "타이레놀정", dosage, "1일 3회 식후",
                    "ITEM-1", "타이레놀정", MatchType.EXACT, "matched",
                    confirmedSlots
            );
            candidate.accept();
            candidate.updateConfirmedMealSlots(confirmedSlots);
            return candidateRepository.save(candidate).getId();
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
