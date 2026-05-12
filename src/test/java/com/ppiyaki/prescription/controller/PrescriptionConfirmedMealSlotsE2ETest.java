package com.ppiyaki.prescription.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

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
@DisplayName("PATCH /prescriptions/{id}/medicines/{candidateId} confirmedMealSlots E2E")
class PrescriptionConfirmedMealSlotsE2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PrescriptionRepository prescriptionRepository;

    @Autowired
    private PrescriptionMedicineCandidateRepository candidateRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private static long userSequence = 700000L;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    @DisplayName("PATCH로 confirmedMealSlots 갱신 후 GET 응답에 노출된다")
    void patch_persists_and_exposes_confirmedMealSlots() {
        // given — 자율형 시니어 + 처방전 + candidate 1건 (suggestedMealSlots 포함)
        final SignupResult senior = signup("자율시니어");
        setSeniorMode(senior.userId(), CareMode.AUTONOMOUS);
        final Long prescriptionId = seedPrescription(senior.userId());
        final Long candidateId = seedCandidate(prescriptionId,
                List.of(com.ppiyaki.medication.MealSlot.BREAKFAST,
                        com.ppiyaki.medication.MealSlot.LUNCH,
                        com.ppiyaki.medication.MealSlot.DINNER));

        // when — 보호자 검수: ACCEPTED + confirmedMealSlots = [LUNCH, DINNER]
        RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + senior.accessToken())
                .body("""
                        {
                            "decision": "ACCEPTED",
                            "confirmedMealSlots": ["LUNCH", "DINNER"]
                        }
                        """)
                .when()
                .patch("/api/v1/prescriptions/" + prescriptionId + "/medicines/" + candidateId)
                .then()
                .statusCode(200);

        // then — GET 응답에 suggested/confirmed 둘 다 노출
        RestAssured.given()
                .header("Authorization", "Bearer " + senior.accessToken())
                .when()
                .get("/api/v1/prescriptions/" + prescriptionId)
                .then()
                .statusCode(200)
                .body("candidates", hasSize(1))
                .body("candidates[0].caregiverDecision", is("ACCEPTED"))
                .body("candidates[0].suggestedMealSlots", contains("BREAKFAST", "LUNCH", "DINNER"))
                .body("candidates[0].confirmedMealSlots", contains("LUNCH", "DINNER"));
    }

    @Test
    @DisplayName("PATCH 본문에 confirmedMealSlots 미포함이면 기존 값 유지 (decision만 갱신)")
    void patch_without_slots_keepsExistingConfirmedSlots() {
        // given
        final SignupResult senior = signup("기존유지시니어");
        setSeniorMode(senior.userId(), CareMode.AUTONOMOUS);
        final Long prescriptionId = seedPrescription(senior.userId());
        final Long candidateId = seedCandidate(prescriptionId, List.of());

        // 1차 PATCH: 슬롯 확정
        RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + senior.accessToken())
                .body("""
                        {"decision": "ACCEPTED", "confirmedMealSlots": ["BREAKFAST"]}
                        """)
                .when()
                .patch("/api/v1/prescriptions/" + prescriptionId + "/medicines/" + candidateId)
                .then()
                .statusCode(200);

        // 2차 PATCH: 결정만 다시(슬롯 필드 미포함)
        RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + senior.accessToken())
                .body("""
                        {"decision": "ACCEPTED"}
                        """)
                .when()
                .patch("/api/v1/prescriptions/" + prescriptionId + "/medicines/" + candidateId)
                .then()
                .statusCode(200);

        // then — 슬롯은 1차 값 유지
        RestAssured.given()
                .header("Authorization", "Bearer " + senior.accessToken())
                .when()
                .get("/api/v1/prescriptions/" + prescriptionId)
                .then()
                .statusCode(200)
                .body("candidates[0].confirmedMealSlots", contains("BREAKFAST"));
    }

    @Test
    @DisplayName("PATCH로 dosage 갱신 후 GET 응답에 노출된다 (정수+단위 분리, unit은 displayValue)")
    void patch_persists_and_exposes_dosage() {
        // given — OCR이 dosage 누락한 candidate
        final SignupResult senior = signup("dosage시니어");
        setSeniorMode(senior.userId(), CareMode.AUTONOMOUS);
        final Long prescriptionId = seedPrescription(senior.userId());
        final Long candidateId = seedCandidateWithDosage(prescriptionId, null, null);

        // when — 보호자가 dosage 보강 (입력은 자유 텍스트, 서버에서 enum 정규화)
        RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + senior.accessToken())
                .body("""
                        {
                            "decision": "ACCEPTED",
                            "dosageQuantity": 1,
                            "dosageUnit": "정"
                        }
                        """)
                .when()
                .patch("/api/v1/prescriptions/" + prescriptionId + "/medicines/" + candidateId)
                .then()
                .statusCode(200);

        // then — GET 응답에 분리 두 필드 노출. unit은 enum.name() 대신 displayValue 문자열
        RestAssured.given()
                .header("Authorization", "Bearer " + senior.accessToken())
                .when()
                .get("/api/v1/prescriptions/" + prescriptionId)
                .then()
                .statusCode(200)
                .body("candidates[0].extractedDosageQuantity", is(1))
                .body("candidates[0].extractedDosageUnit", is("정"));
    }

    @Test
    @DisplayName("PATCH 본문에 dosage 미포함이면 기존 dosage 유지")
    void patch_without_dosage_keepsExistingDosage() {
        // given — 기존 dosage가 있는 candidate
        final SignupResult senior = signup("dosage유지시니어");
        setSeniorMode(senior.userId(), CareMode.AUTONOMOUS);
        final Long prescriptionId = seedPrescription(senior.userId());
        final Long candidateId = seedCandidateWithDosage(prescriptionId,
                java.math.BigDecimal.ONE, com.ppiyaki.medication.DosageUnit.TABLET);

        // when — dosage 필드 미포함
        RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + senior.accessToken())
                .body("""
                        {"decision": "ACCEPTED"}
                        """)
                .when()
                .patch("/api/v1/prescriptions/" + prescriptionId + "/medicines/" + candidateId)
                .then()
                .statusCode(200);

        // then — 기존 dosage 유지
        RestAssured.given()
                .header("Authorization", "Bearer " + senior.accessToken())
                .when()
                .get("/api/v1/prescriptions/" + prescriptionId)
                .then()
                .statusCode(200)
                .body("candidates[0].extractedDosageQuantity", is(1))
                .body("candidates[0].extractedDosageUnit", is("정"));
    }

    @Test
    @DisplayName("잘못된 enum 값이면 400")
    void patch_invalidEnum_returns400() {
        final SignupResult senior = signup("잘못된슬롯시니어");
        setSeniorMode(senior.userId(), CareMode.AUTONOMOUS);
        final Long prescriptionId = seedPrescription(senior.userId());
        final Long candidateId = seedCandidate(prescriptionId, List.of());

        RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + senior.accessToken())
                .body("""
                        {"decision": "ACCEPTED", "confirmedMealSlots": ["BEDTIME"]}
                        """)
                .when()
                .patch("/api/v1/prescriptions/" + prescriptionId + "/medicines/" + candidateId)
                .then()
                .statusCode(400);
    }

    private SignupResult signup(final String nickname) {
        final String loginId = "presslot" + userSequence++;
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

    private Long seedPrescription(final Long seniorId) {
        return transactionTemplate.execute(status -> {
            final Prescription prescription = new Prescription(seniorId);
            setHierarchicalField(prescription, "status", PrescriptionStatus.PENDING_REVIEW);
            return prescriptionRepository.save(prescription).getId();
        });
    }

    private Long seedCandidate(
            final Long prescriptionId,
            final List<com.ppiyaki.medication.MealSlot> suggestedSlots
    ) {
        return transactionTemplate.execute(status -> {
            final PrescriptionMedicineCandidate candidate = new PrescriptionMedicineCandidate(
                    prescriptionId, "raw", "타이레놀정",
                    java.math.BigDecimal.ONE, com.ppiyaki.medication.DosageUnit.TABLET,
                    "1일 3회 식후",
                    "ITEM-1", "타이레놀정", MatchType.EXACT, "matched",
                    suggestedSlots
            );
            return candidateRepository.save(candidate).getId();
        });
    }

    private Long seedCandidateWithDosage(
            final Long prescriptionId,
            final java.math.BigDecimal quantity,
            final com.ppiyaki.medication.DosageUnit unit
    ) {
        return transactionTemplate.execute(status -> {
            final PrescriptionMedicineCandidate candidate = new PrescriptionMedicineCandidate(
                    prescriptionId, "raw", "타이레놀정",
                    quantity, unit,
                    "1일 3회 식후",
                    "ITEM-1", "타이레놀정", MatchType.EXACT, "matched",
                    List.of()
            );
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
