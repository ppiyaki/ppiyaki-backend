package com.ppiyaki.prescription.controller;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import com.ppiyaki.prescription.Prescription;
import com.ppiyaki.prescription.PrescriptionStatus;
import com.ppiyaki.prescription.repository.PrescriptionRepository;
import com.ppiyaki.user.CareRelation;
import com.ppiyaki.user.repository.CareRelationRepository;
import com.ppiyaki.user.repository.UserRepository;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import java.lang.reflect.Field;
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
@DisplayName("GET /api/v1/prescriptions ?seniorId= E2E")
class PrescriptionListControllerE2ETest {

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

    private static long userSequence = 500000L;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    @DisplayName("seniorId 미지정 → 호출자 본인 처방전만 반환")
    void list_noSeniorId_returnsOwnPrescriptions() {
        final SignupResult senior = signup("본인유저");
        seedPrescription(senior.userId(), PrescriptionStatus.PENDING_REVIEW);
        seedPrescription(senior.userId(), PrescriptionStatus.CONFIRMED);

        // 다른 시니어의 처방전은 안 나와야 함
        final SignupResult other = signup("타인");
        seedPrescription(other.userId(), PrescriptionStatus.PENDING_REVIEW);

        RestAssured.given()
                .header("Authorization", "Bearer " + senior.accessToken())
                .when()
                .get("/api/v1/prescriptions")
                .then()
                .statusCode(200)
                .body("responses", hasSize(2));
    }

    @Test
    @DisplayName("연동 보호자 ?seniorId=senior → 시니어 처방전 목록 반환")
    void list_caregiver_withSeniorId_returnsSeniorPrescriptions() {
        final SignupResult senior = signup("시니어A");
        seedPrescription(senior.userId(), PrescriptionStatus.PENDING_REVIEW);
        seedPrescription(senior.userId(), PrescriptionStatus.CONFIRMED);

        final SignupResult caregiver = signup("보호자A");
        seedCareRelation(senior.userId(), caregiver.userId());

        RestAssured.given()
                .header("Authorization", "Bearer " + caregiver.accessToken())
                .when()
                .get("/api/v1/prescriptions?seniorId=" + senior.userId())
                .then()
                .statusCode(200)
                .body("responses", hasSize(2));
    }

    @Test
    @DisplayName("미연동 보호자 ?seniorId=senior → 403 CARE_001")
    void list_unrelated_withSeniorId_returns403() {
        final SignupResult senior = signup("시니어B");
        seedPrescription(senior.userId(), PrescriptionStatus.PENDING_REVIEW);

        final SignupResult stranger = signup("타인B");

        RestAssured.given()
                .header("Authorization", "Bearer " + stranger.accessToken())
                .when()
                .get("/api/v1/prescriptions?seniorId=" + senior.userId())
                .then()
                .statusCode(403)
                .body("error.code", is("CARE_001"));
    }

    @Test
    @DisplayName("status 필터 + seniorId 결합 → 해당 status인 시니어 처방전만 반환")
    void list_caregiver_withSeniorIdAndStatus_filters() {
        final SignupResult senior = signup("시니어C");
        seedPrescription(senior.userId(), PrescriptionStatus.PENDING_REVIEW);
        seedPrescription(senior.userId(), PrescriptionStatus.CONFIRMED);
        seedPrescription(senior.userId(), PrescriptionStatus.CONFIRMED);

        final SignupResult caregiver = signup("보호자C");
        seedCareRelation(senior.userId(), caregiver.userId());

        RestAssured.given()
                .header("Authorization", "Bearer " + caregiver.accessToken())
                .when()
                .get("/api/v1/prescriptions?seniorId=" + senior.userId() + "&status=CONFIRMED")
                .then()
                .statusCode(200)
                .body("responses", hasSize(2))
                .body("responses.status", everyItem(is("CONFIRMED")));
    }

    private SignupResult signup(final String nickname) {
        final String loginId = "preslist" + userSequence++;
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
        careRelationRepository.save(new CareRelation(seniorId, caregiverId, "INV-" + seniorId + "-" + caregiverId));
    }

    private void seedPrescription(final Long ownerId, final PrescriptionStatus status) {
        transactionTemplate.executeWithoutResult(tx -> {
            final Prescription prescription = new Prescription(ownerId);
            setHierarchicalField(prescription, "status", status);
            prescriptionRepository.save(prescription);
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
