package com.ppiyaki.notification.controller;

import com.ppiyaki.common.auth.JwtProvider;
import com.ppiyaki.medication.domain.DosageUnit;
import com.ppiyaki.medication.domain.MealSlot;
import com.ppiyaki.medication.domain.MedicationSchedule;
import com.ppiyaki.medication.repository.MedicationScheduleRepository;
import com.ppiyaki.medicine.Medicine;
import com.ppiyaki.medicine.repository.MedicineRepository;
import com.ppiyaki.notification.Notification;
import com.ppiyaki.notification.NotificationCategory;
import com.ppiyaki.notification.repository.NotificationRepository;
import com.ppiyaki.user.domain.CareMode;
import com.ppiyaki.user.domain.CareRelation;
import com.ppiyaki.user.domain.User;
import com.ppiyaki.user.domain.UserRole;
import com.ppiyaki.user.repository.CareRelationRepository;
import com.ppiyaki.user.repository.UserRepository;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.assertj.core.api.Assertions;
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
@DisplayName("끼니별 복약 완료 알림 발송 E2E (MEDICATION_COMPLETE)")
class MedicationCompleteNotificationE2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private MedicineRepository medicineRepository;
    @Autowired
    private MedicationScheduleRepository scheduleRepository;
    @Autowired
    private CareRelationRepository careRelationRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private JwtProvider jwtProvider;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private static long userSequence = 900000L;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    @DisplayName("끼니의 모든 schedule 인증 시 보호자 알림함에 MEDICATION_COMPLETE row가 끼니 단위로 저장된다 (AFTER_COMMIT 커밋 보장)")
    void slotComplete_persistsNotificationForCaregiver() {
        // given — 시니어 + 보호자 연동 + BREAKFAST schedule 1정
        final Long seniorId = seedSenior();
        final Long caregiverId = seedCaregiver();
        seedRelation(seniorId, caregiverId);
        final String seniorToken = jwtProvider.createAccessToken(seniorId, UserRole.SENIOR.name());
        final Long medicineId = seedMedicine(seniorId);
        final Long scheduleId = seedSchedule(medicineId, MealSlot.BREAKFAST);
        final LocalDate today = LocalDate.now();

        // when — 시니어가 BREAKFAST schedule TAKEN 인증 → 아침 끼니 완료
        certifyTaken(seniorToken, scheduleId, today);

        // then — 보호자 알림함에 아침 완료 알림 1건 (AFTER_COMMIT 리스너 저장이 실제 커밋되어야 함)
        final List<Notification> completed = findCompleteNotifications(caregiverId);
        Assertions.assertThat(completed).hasSize(1);
        Assertions.assertThat(completed.get(0).getSeniorId()).isEqualTo(seniorId);
        Assertions.assertThat(completed.get(0).getTargetDate()).isEqualTo(today);
        Assertions.assertThat(completed.get(0).getMealSlot()).isEqualTo(MealSlot.BREAKFAST.name());
        Assertions.assertThat(completed.get(0).getBody()).contains("님이 아침 복약을 완료했어요");
    }

    @Test
    @DisplayName("끼니에 schedule이 2개인데 1개만 인증되면 완료 알림이 발송되지 않는다")
    void slotPartiallyTaken_noNotification() {
        final Long seniorId = seedSenior();
        final Long caregiverId = seedCaregiver();
        seedRelation(seniorId, caregiverId);
        final String seniorToken = jwtProvider.createAccessToken(seniorId, UserRole.SENIOR.name());
        final Long medicineId = seedMedicine(seniorId);
        final Long scheduleA = seedSchedule(medicineId, MealSlot.BREAKFAST);
        seedSchedule(medicineId, MealSlot.BREAKFAST);
        final LocalDate today = LocalDate.now();

        // when — BREAKFAST 2정 중 1정만 인증
        certifyTaken(seniorToken, scheduleA, today);

        // then — 아침 끼니가 아직 완료되지 않았으므로 알림 없음
        Assertions.assertThat(findCompleteNotifications(caregiverId)).isEmpty();
    }

    @Test
    @DisplayName("아침/점심을 각각 완료하면 끼니별로 알림이 1건씩 쌓이고, 같은 끼니 재인증은 중복 발송되지 않는다")
    void perSlot_independentAndIdempotent() {
        final Long seniorId = seedSenior();
        final Long caregiverId = seedCaregiver();
        seedRelation(seniorId, caregiverId);
        final String seniorToken = jwtProvider.createAccessToken(seniorId, UserRole.SENIOR.name());
        final Long medicineId = seedMedicine(seniorId);
        final Long breakfastScheduleId = seedSchedule(medicineId, MealSlot.BREAKFAST);
        final Long lunchScheduleId = seedSchedule(medicineId, MealSlot.LUNCH);
        final LocalDate today = LocalDate.now();

        // 아침 완료 → 아침 알림 1건
        certifyTaken(seniorToken, breakfastScheduleId, today);
        Assertions.assertThat(findCompleteNotifications(caregiverId))
                .extracting(Notification::getMealSlot)
                .containsExactlyInAnyOrder(MealSlot.BREAKFAST.name());

        // 점심 완료 → 점심 알림 추가 (총 2건)
        certifyTaken(seniorToken, lunchScheduleId, today);
        Assertions.assertThat(findCompleteNotifications(caregiverId))
                .extracting(Notification::getMealSlot)
                .containsExactlyInAnyOrder(MealSlot.BREAKFAST.name(), MealSlot.LUNCH.name());

        // 아침 재인증(멱등 호출) → 중복 발송 없음 (여전히 2건)
        certifyTaken(seniorToken, breakfastScheduleId, today);
        Assertions.assertThat(findCompleteNotifications(caregiverId)).hasSize(2);
    }

    // --- helpers ---

    private void certifyTaken(final String seniorToken, final Long scheduleId, final LocalDate today) {
        RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + seniorToken)
                .body("""
                        {"scheduleId": %d, "targetDate": "%s", "status": "TAKEN"}
                        """.formatted(scheduleId, today))
                .when()
                .put("/api/v1/medication-logs")
                .then()
                .statusCode(200);
    }

    private List<Notification> findCompleteNotifications(final Long caregiverId) {
        return notificationRepository.findAll().stream()
                .filter(n -> n.getUserId().equals(caregiverId))
                .filter(n -> n.getCategory() == NotificationCategory.MEDICATION_COMPLETE)
                .toList();
    }

    // --- fixtures ---

    private Long seedSenior() {
        return transactionTemplate.execute(status -> {
            final User senior = User.createSenior("완료시니어" + userSequence++, (LocalDate) null);
            senior.changeCareMode(CareMode.AUTONOMOUS);
            return userRepository.save(senior).getId();
        });
    }

    private Long seedCaregiver() {
        return transactionTemplate.execute(status -> {
            final User caregiver = User.createSenior("완료보호자" + userSequence++, (LocalDate) null);
            caregiver.assignRole(UserRole.CAREGIVER);
            return userRepository.save(caregiver).getId();
        });
    }

    private void seedRelation(final Long seniorId, final Long caregiverId) {
        transactionTemplate.executeWithoutResult(status -> careRelationRepository.save(CareRelation.createLinked(
                seniorId, caregiverId)));
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
}
