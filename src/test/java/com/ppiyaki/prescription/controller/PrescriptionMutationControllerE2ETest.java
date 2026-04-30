package com.ppiyaki.prescription.controller;

import static org.hamcrest.Matchers.is;

import com.ppiyaki.prescription.Prescription;
import com.ppiyaki.prescription.PrescriptionStatus;
import com.ppiyaki.prescription.repository.PrescriptionRepository;
import com.ppiyaki.user.CareMode;
import com.ppiyaki.user.CareRelation;
import com.ppiyaki.user.User;
import com.ppiyaki.user.repository.CareRelationRepository;
import com.ppiyaki.user.repository.UserRepository;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
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
@DisplayName("처방전 변경 엔드포인트 권한 분기 E2E")
class PrescriptionMutationControllerE2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CareRelationRepository careRelationRepository;

    @Autowired
    private PrescriptionRepository prescriptionRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static long userSequence = 800000L;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    @DisplayName("AUTONOMOUS 시니어 본인이 POST /medicines 호출하면 201")
    void autonomous_senior_can_add_medicine() {
        // given
        final SignupResult senior = signup("자율형시니어");
        setSeniorMode(senior.userId(), CareMode.AUTONOMOUS);
        final Long prescriptionId = seedPrescription(senior.userId(), LocalDateTime.now());

        // when & then
        addMedicine(senior.accessToken(), prescriptionId)
                .then()
                .statusCode(201);
    }

    @Test
    @DisplayName("MANAGED 시니어 본인이 0~72h 사이에 POST /medicines 호출하면 403 CARE_004")
    void managed_senior_blocked_within_72h() {
        // given
        final SignupResult senior = signup("관리형시니어A");
        // default = MANAGED
        final Long prescriptionId = seedPrescription(senior.userId(), LocalDateTime.now().minusHours(1));

        // when & then
        addMedicine(senior.accessToken(), prescriptionId)
                .then()
                .statusCode(403)
                .body("error.code", is("CARE_004"));
    }

    @Test
    @DisplayName("MANAGED 시니어 본인이 72h 경과 후 POST /medicines 호출하면 fallback 통과")
    void managed_senior_fallback_after_72h() {
        // given
        final SignupResult senior = signup("관리형시니어B");
        // default = MANAGED
        final Long prescriptionId = seedPrescription(senior.userId(), LocalDateTime.now().minusHours(73));

        // when & then
        addMedicine(senior.accessToken(), prescriptionId)
                .then()
                .statusCode(201);
    }

    @Test
    @DisplayName("활성 보호자가 MANAGED 모드 시니어의 처방전에 POST /medicines 호출하면 201")
    void caregiver_can_add_medicine_under_managed() {
        // given
        final SignupResult senior = signup("관리형시니어C");
        final SignupResult caregiver = signup("보호자G");
        seedCareRelation(senior.userId(), caregiver.userId());
        final Long prescriptionId = seedPrescription(senior.userId(), LocalDateTime.now());

        // when & then
        addMedicine(caregiver.accessToken(), prescriptionId)
                .then()
                .statusCode(201);
    }

    @Test
    @DisplayName("관계 없는 사용자가 POST /medicines 호출하면 403 CARE_001")
    void unrelated_user_rejected() {
        // given
        final SignupResult senior = signup("관리형시니어D");
        final SignupResult stranger = signup("타인H");
        final Long prescriptionId = seedPrescription(senior.userId(), LocalDateTime.now());

        // when & then
        addMedicine(stranger.accessToken(), prescriptionId)
                .then()
                .statusCode(403)
                .body("error.code", is("CARE_001"));
    }

    @Test
    @DisplayName("AUTONOMOUS로 모드 전환 후 시니어가 즉시 변경 가능 — 모드 효과 통합 검증")
    void mode_change_effect_integration() {
        // given — MANAGED 상태에서 시니어 차단 확인
        final SignupResult senior = signup("통합시니어");
        final SignupResult caregiver = signup("통합보호자");
        seedCareRelation(senior.userId(), caregiver.userId());
        final Long prescriptionId = seedPrescription(senior.userId(), LocalDateTime.now());

        addMedicine(senior.accessToken(), prescriptionId).then().statusCode(403);

        // when — 보호자가 AUTONOMOUS로 전환
        RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + caregiver.accessToken())
                .body("""
                        {"careMode": "AUTONOMOUS"}
                        """)
                .when()
                .put("/api/v1/users/" + senior.userId() + "/care-mode")
                .then()
                .statusCode(200);

        // then — 시니어가 즉시 변경 가능
        addMedicine(senior.accessToken(), prescriptionId).then().statusCode(201);
    }

    private io.restassured.response.Response addMedicine(final String token, final Long prescriptionId) {
        return RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + token)
                .body("""
                        {
                            "itemSeq": "199500096",
                            "itemName": "타이레놀정 500mg",
                            "dosage": "1정",
                            "schedule": "1일 3회"
                        }
                        """)
                .when()
                .post("/api/v1/prescriptions/" + prescriptionId + "/medicines");
    }

    private SignupResult signup(final String nickname) {
        final String loginId = "presmut" + userSequence++;
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

    @Transactional
    void setSeniorMode(final Long seniorId, final CareMode mode) {
        transactionTemplate.executeWithoutResult(status -> {
            final User user = userRepository.findById(seniorId).orElseThrow();
            user.changeCareMode(mode);
            userRepository.save(user);
        });
    }

    private void seedCareRelation(final Long seniorId, final Long caregiverId) {
        careRelationRepository.save(new CareRelation(seniorId, caregiverId, "INV-" + seniorId + "-" + caregiverId));
    }

    private Long seedPrescription(final Long seniorId, final LocalDateTime createdAt) {
        final Long id = transactionTemplate.execute(status -> {
            final Prescription prescription = new Prescription(seniorId);
            setHierarchicalField(prescription, "status", PrescriptionStatus.PENDING_REVIEW);
            return prescriptionRepository.save(prescription).getId();
        });
        // created_at은 @CreatedDate + updatable=false라 JPA로는 갱신 불가 → 직접 SQL 실행
        jdbcTemplate.update("UPDATE prescriptions SET created_at = ? WHERE id = ?",
                java.sql.Timestamp.valueOf(createdAt), id);
        return id;
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
