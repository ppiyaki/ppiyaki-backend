package com.ppiyaki.notification.controller;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import com.ppiyaki.common.auth.JwtProvider;
import com.ppiyaki.medication.domain.DosageUnit;
import com.ppiyaki.medication.domain.MealSlot;
import com.ppiyaki.medication.domain.MedicationSchedule;
import com.ppiyaki.medication.repository.MedicationScheduleRepository;
import com.ppiyaki.medicine.Medicine;
import com.ppiyaki.medicine.repository.MedicineRepository;
import com.ppiyaki.notification.Notification;
import com.ppiyaki.notification.repository.NotificationRepository;
import com.ppiyaki.user.domain.User;
import com.ppiyaki.user.domain.UserRole;
import com.ppiyaki.user.repository.UserRepository;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "openai.api-key=sk-test-placeholder",
        "openai.model=gpt-test",
        "ncp.storage.endpoint=https://kr.object.ncloudstorage.com",
        "ncp.storage.region=kr-standard",
        "ncp.storage.access-key=test-access-key",
        "ncp.storage.secret-key=test-secret-key",
        "ncp.storage.bucket-name=ppiyaki-test"
})
@DisplayName("PUT /api/v1/medication-logs TAKEN → MEDICATION_REMINDER 알림 takenAt 자동 전이 E2E")
class NotificationAutoTakenE2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private MedicineRepository medicineRepository;
    @Autowired
    private MedicationScheduleRepository scheduleRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private JwtProvider jwtProvider;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private static long userSequence = 800000L;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    @DisplayName("TAKEN upsert 시 같은 날짜·슬롯의 MEDICATION_REMINDER 알림만 takenAt 채워지고 다른 카테고리는 영향 없음")
    void taken_upsert_marks_reminder_taken_only() {
        // given — 시니어 + medicine + schedule(BREAKFAST 1정) + 시니어 토큰
        final Long seniorId = seedSenior();
        final String seniorToken = jwtProvider.createAccessToken(seniorId, UserRole.SENIOR.name());
        final Long medicineId = seedMedicine(seniorId);
        final Long scheduleId = seedSchedule(medicineId, MealSlot.BREAKFAST);

        // 알림 3건 INSERT
        // (1) MEDICATION_REMINDER, BREAKFAST, today  → 자동 전이 대상
        // (2) MEDICATION_REMINDER, DINNER, today     → 다른 슬롯, 영향 없음
        // (3) DUR_WARNING                            → 다른 카테고리, 영향 없음
        final LocalDate today = LocalDate.now();
        final Long reminderBreakfastId = seedReminderNotification(seniorId, today, MealSlot.BREAKFAST);
        final Long reminderDinnerId = seedReminderNotification(seniorId, today, MealSlot.DINNER);
        final Long durWarningId = seedDurWarningNotification(seniorId);

        // when — 시니어가 BREAKFAST schedule TAKEN 인증
        RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + seniorToken)
                .body("""
                        {
                            "scheduleId": %d,
                            "targetDate": "%s",
                            "status": "TAKEN"
                        }
                        """.formatted(scheduleId, today))
                .when()
                .put("/api/v1/medication-logs")
                .then()
                .statusCode(200);

        // then — DB에서 직접 검증 (응답 DTO 검증은 GET /notifications로 별도 처리)
        final Notification reminderBreakfast = notificationRepository.findById(reminderBreakfastId).orElseThrow();
        final Notification reminderDinner = notificationRepository.findById(reminderDinnerId).orElseThrow();
        final Notification durWarning = notificationRepository.findById(durWarningId).orElseThrow();

        org.assertj.core.api.Assertions.assertThat(reminderBreakfast.getTakenAt()).isNotNull();
        org.assertj.core.api.Assertions.assertThat(reminderDinner.getTakenAt())
                .as("다른 슬롯(DINNER) 알림은 영향 없어야 함").isNull();
        org.assertj.core.api.Assertions.assertThat(durWarning.getTakenAt())
                .as("다른 카테고리(DUR_WARNING) 알림은 영향 없어야 함").isNull();

        // GET 응답 DTO에 takenAt 노출 검증
        RestAssured.given()
                .header("Authorization", "Bearer " + seniorToken)
                .when()
                .get("/api/v1/notifications?category=MEDICATION_REMINDER")
                .then()
                .statusCode(200)
                .body("responses.find { it.id == " + reminderBreakfastId + " }.takenAt", notNullValue())
                .body("responses.find { it.id == " + reminderDinnerId + " }.takenAt", is(nullValue()));
    }

    @Test
    @DisplayName("이미 TAKEN인 row 재호출(MISSED→TAKEN→TAKEN) 시 첫 TAKEN의 takenAt이 보존되고 두 번째 호출이 덮어쓰지 않음 — 멱등")
    void reminder_takenAt_idempotent() throws InterruptedException {
        final Long seniorId = seedSenior();
        final String seniorToken = jwtProvider.createAccessToken(seniorId, UserRole.SENIOR.name());
        final Long medicineId = seedMedicine(seniorId);
        final Long scheduleId = seedSchedule(medicineId, MealSlot.LUNCH);
        final LocalDate today = LocalDate.now();
        final Long reminderId = seedReminderNotification(seniorId, today, MealSlot.LUNCH);

        // 1차 TAKEN
        RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + seniorToken)
                .body("""
                        {"scheduleId": %d, "targetDate": "%s", "status": "TAKEN"}
                        """.formatted(scheduleId, today))
                .when().put("/api/v1/medication-logs").then().statusCode(200);

        final LocalDateTime firstTakenAt = notificationRepository.findById(reminderId).orElseThrow().getTakenAt();
        org.assertj.core.api.Assertions.assertThat(firstTakenAt).isNotNull();

        // 2차 TAKEN (멱등 호출)
        Thread.sleep(20);
        RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + seniorToken)
                .body("""
                        {"scheduleId": %d, "targetDate": "%s", "status": "TAKEN"}
                        """.formatted(scheduleId, today))
                .when().put("/api/v1/medication-logs").then().statusCode(200);

        final LocalDateTime secondTakenAt = notificationRepository.findById(reminderId).orElseThrow().getTakenAt();
        org.assertj.core.api.Assertions.assertThat(secondTakenAt)
                .as("이미 TAKEN인 알림의 takenAt은 덮어쓰지 않아야 함 (markReminderTaken WHERE takenAt IS NULL)")
                .isEqualTo(firstTakenAt);
    }

    // --- fixtures ---

    private Long seedSenior() {
        return transactionTemplate.execute(status -> {
            final User senior = User.createSenior("E2E시니어" + userSequence++, (LocalDate) null);
            return userRepository.save(senior).getId();
        });
    }

    private Long seedMedicine(final Long seniorId) {
        return transactionTemplate.execute(status -> {
            final Medicine medicine = new Medicine(seniorId, null, "테스트약", 30, 30, "ITEM-1", null);
            return medicineRepository.save(medicine).getId();
        });
    }

    private Long seedSchedule(final Long medicineId, final MealSlot slot) {
        return transactionTemplate.execute(status -> {
            final MedicationSchedule schedule = new MedicationSchedule(
                    medicineId, slot, BigDecimal.ONE, DosageUnit.TABLET,
                    "DAILY", LocalDate.now(), null);
            return scheduleRepository.save(schedule).getId();
        });
    }

    private Long seedReminderNotification(final Long seniorId, final LocalDate targetDate, final MealSlot slot) {
        return transactionTemplate.execute(status -> {
            final Notification n = Notification.createForMedicationReminder(
                    seniorId, "약 드실 시간이에요", "삐~약드실 시간이에요~", targetDate, slot.name());
            return notificationRepository.save(n).getId();
        });
    }

    private Long seedDurWarningNotification(final Long seniorId) {
        return transactionTemplate.execute(status -> {
            final Notification n = Notification.createForDurWarning(
                    seniorId, seniorId, "DUR 경고", "약 충돌 경고", 999L);
            return notificationRepository.save(n).getId();
        });
    }
}
