package com.ppiyaki.notification.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import com.ppiyaki.outbox.MedicationCompleteOutboxRelay;
import com.ppiyaki.outbox.OutboxMessage;
import com.ppiyaki.outbox.OutboxStatus;
import com.ppiyaki.outbox.repository.OutboxMessageRepository;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "openai.api-key=sk-test-placeholder",
        "openai.model=gpt-test",
        "ncp.storage.endpoint=https://kr.object.ncloudstorage.com",
        "ncp.storage.region=kr-standard",
        "ncp.storage.access-key=test-access-key",
        "ncp.storage.secret-key=test-secret-key",
        "ncp.storage.bucket-name=ppiyaki-test",
        // 백그라운드 @Scheduled 릴레이가 테스트 도중 끼어들지 않게 initial delay를 크게 잡고,
        // 테스트에서 relay.poll()을 직접 호출해 결정적으로 처리한다.
        "outbox.relay.initial-delay-ms=3600000"
})
@DisplayName("끼니별 복약 완료 알림 발송 E2E (MEDICATION_COMPLETE, Outbox relay 경유)")
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
    @Autowired
    private MedicationCompleteOutboxRelay relay;
    @Autowired
    private OutboxMessageRepository outboxMessageRepository;

    private static long userSequence = 900000L;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        // 공유 H2(mem, ddl-auto=update)라 다른 테스트가 남긴 outbox row가 relay.poll()에
        // 클레임되거나 payload 검증에 섞이지 않게 비운다.
        outboxMessageRepository.deleteAll();
    }

    @Test
    @DisplayName("끼니의 모든 schedule 인증 시 outbox 메시지가 적재되고, relay 처리 후 보호자 알림함에 MEDICATION_COMPLETE row가 끼니 단위로 저장된다")
    void slotComplete_persistsNotificationForCaregiver() {
        // given — 시니어 + 보호자 연동 + BREAKFAST schedule 1정
        final Long seniorId = seedSenior();
        final Long caregiverId = seedCaregiver();
        seedRelation(seniorId, caregiverId);
        final String seniorToken = jwtProvider.createAccessToken(seniorId, UserRole.SENIOR.name());
        final Long medicineId = seedMedicine(seniorId);
        final Long scheduleId = seedSchedule(medicineId, MealSlot.BREAKFAST);
        final LocalDate today = LocalDate.now();

        // when — 시니어가 BREAKFAST schedule TAKEN 인증 → 아침 끼니 완료 (API 커밋 = outbox INSERT 커밋)
        certifyTaken(seniorToken, scheduleId, today);
        // 알림은 이제 비동기(outbox relay) 경로 — 테스트에서는 relay를 직접 호출해 결정적으로 처리
        relay.poll();

        // then — 보호자 알림함에 아침 완료 알림 1건
        final List<Notification> completed = findCompleteNotifications(caregiverId);
        assertThat(completed).hasSize(1);
        assertThat(completed.get(0).getSeniorId()).isEqualTo(seniorId);
        assertThat(completed.get(0).getTargetDate()).isEqualTo(today);
        assertThat(completed.get(0).getMealSlot()).isEqualTo(MealSlot.BREAKFAST.name());
        assertThat(completed.get(0).getBody()).contains("님이 아침 복약을 완료했어요");

        // 처리된 outbox 메시지는 PROCESSED로 마킹된다 (해당 시니어 payload 기준)
        // seniorId 뒤에 구분자(,)까지 포함해 매칭한다. 단순 prefix 매칭이면 다른 테스트가 남긴
        // 더 긴 seniorId(예: 999911) payload가 오탐될 수 있다.
        final List<OutboxMessage> processed = outboxMessageRepository.findAll().stream()
                .filter(m -> m.getPayload() != null && m.getPayload().contains("\"seniorId\":" + seniorId + ","))
                .toList();
        assertThat(processed).hasSize(1);
        assertThat(processed.get(0).getStatus()).isEqualTo(OutboxStatus.PROCESSED);
    }

    @Test
    @DisplayName("끼니에 schedule이 2개여도 (약별이 아닌 끼니 단위 인증) 사진 없는 인증 1건으로 슬롯 전체가 완료되어 알림이 발송된다")
    void slotSingleManualAuth_completesWholeSlot() {
        // 약별로 인증 사진을 따로 찍지 않고 끼니 단위로 인증하는 구조이므로, 사진 없는 수동 인증 1건은
        // 같은 끼니의 다른 schedule들로 TAKEN이 전파된다 (b6fd662). 따라서 끼니 전체가 완료 처리된다.
        final Long seniorId = seedSenior();
        final Long caregiverId = seedCaregiver();
        seedRelation(seniorId, caregiverId);
        final String seniorToken = jwtProvider.createAccessToken(seniorId, UserRole.SENIOR.name());
        final Long medicineId = seedMedicine(seniorId);
        final Long scheduleA = seedSchedule(medicineId, MealSlot.BREAKFAST);
        seedSchedule(medicineId, MealSlot.BREAKFAST);
        final LocalDate today = LocalDate.now();

        // when — BREAKFAST 2개 schedule 중 1건만 (사진 없이) 인증 후 relay 처리
        certifyTaken(seniorToken, scheduleA, today);
        relay.poll();

        // then — 끼니 전체로 전파되어 완료 알림 1건 발송
        assertThat(findCompleteNotifications(caregiverId))
                .extracting(Notification::getMealSlot)
                .containsExactly(MealSlot.BREAKFAST.name());
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

        // 아침 완료 → relay 처리 → 아침 알림 1건
        certifyTaken(seniorToken, breakfastScheduleId, today);
        relay.poll();
        assertThat(findCompleteNotifications(caregiverId))
                .extracting(Notification::getMealSlot)
                .containsExactlyInAnyOrder(MealSlot.BREAKFAST.name());

        // 점심 완료 → relay 처리 → 점심 알림 추가 (총 2건)
        certifyTaken(seniorToken, lunchScheduleId, today);
        relay.poll();
        assertThat(findCompleteNotifications(caregiverId))
                .extracting(Notification::getMealSlot)
                .containsExactlyInAnyOrder(MealSlot.BREAKFAST.name(), MealSlot.LUNCH.name());

        // 아침 재인증(멱등 호출) → outbox 재적재 없음 + relay 재실행에도 중복 발송 없음 (여전히 2건)
        certifyTaken(seniorToken, breakfastScheduleId, today);
        relay.poll();
        assertThat(findCompleteNotifications(caregiverId)).hasSize(2);
    }

    @Test
    @DisplayName("동일 자연키 MEDICATION_COMPLETE 2회 저장 시 유니크 제약(sentinel schedule_id)으로 중복 저장이 거부된다")
    void duplicateNaturalKey_rejectedByUniqueConstraint() {
        // 실제 dispatch 경로가 아니라, 동시성 레이스로 exists 체크를 둘 다 통과해 save가 2번 도달한
        // 상황을 재현. schedule_id가 NULL이면(구 스키마) H2/MySQL 모두 NULL을 서로 다르게 취급해
        // 중복이 통과되지만, sentinel(0)로 채워지면 유니크 제약이 두 번째 저장을 거부해야 한다.
        final Long caregiverId = userSequence++;
        final Long seniorId = userSequence++;
        final LocalDate today = LocalDate.now();

        notificationRepository.saveAndFlush(Notification.createForMedicationComplete(
                caregiverId, seniorId, "완료", "아침 복약 완료", today, MealSlot.BREAKFAST.name()));

        assertThatThrownBy(() -> notificationRepository.saveAndFlush(
                Notification.createForMedicationComplete(
                        caregiverId, seniorId, "완료2", "아침 복약 완료2", today, MealSlot.BREAKFAST.name())))
                .isInstanceOf(DataIntegrityViolationException.class);

        final long persisted = notificationRepository.findAll().stream()
                .filter(n -> n.getUserId().equals(caregiverId))
                .filter(n -> n.getCategory() == NotificationCategory.MEDICATION_COMPLETE)
                .count();
        assertThat(persisted).isEqualTo(1);
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
