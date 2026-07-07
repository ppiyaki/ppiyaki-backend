package com.ppiyaki.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.ppiyaki.medication.domain.DosageUnit;
import com.ppiyaki.medication.domain.LogStatus;
import com.ppiyaki.medication.domain.MealSlot;
import com.ppiyaki.medication.domain.MedicationLog;
import com.ppiyaki.medication.domain.MedicationSchedule;
import com.ppiyaki.medication.event.MedicationTakenEvent;
import com.ppiyaki.medication.repository.MedicationLogRepository;
import com.ppiyaki.medication.repository.MedicationScheduleRepository;
import com.ppiyaki.medicine.Medicine;
import com.ppiyaki.medicine.repository.MedicineRepository;
import com.ppiyaki.notification.Notification;
import com.ppiyaki.notification.NotificationCategory;
import com.ppiyaki.notification.repository.NotificationRepository;
import com.ppiyaki.outbox.repository.OutboxMessageRepository;
import com.ppiyaki.user.domain.CareMode;
import com.ppiyaki.user.domain.CareRelation;
import com.ppiyaki.user.domain.User;
import com.ppiyaki.user.domain.UserRole;
import com.ppiyaki.user.repository.CareRelationRepository;
import com.ppiyaki.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Outbox relay <b>멱등성</b> 검증: 같은 PENDING 메시지에 대해 relay가 여러 번 돌아도
 * 알림은 정확히 1건만 생성되고(6컬럼 유니크 제약 + exists 선체크), 메시지는 PROCESSED로 종결된다.
 */
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
@DisplayName("복약 완료 Outbox relay 멱등성 (poll 2회 → 알림 1건)")
class MedicationCompleteOutboxRelayIdempotencyTest {

    @Autowired
    private MedicationCompleteOutboxRelay relay;
    @Autowired
    private OutboxService outboxService;
    @Autowired
    private OutboxMessageRepository outboxMessageRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CareRelationRepository careRelationRepository;
    @Autowired
    private MedicineRepository medicineRepository;
    @Autowired
    private MedicationScheduleRepository scheduleRepository;
    @Autowired
    private MedicationLogRepository logRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private static long userSequence = 910000L;

    @BeforeEach
    void setUp() {
        // 공유 H2(mem, ddl-auto=update)라 다른 테스트가 남긴 PENDING row가 poll에 끼어들지 않게 비운다.
        outboxMessageRepository.deleteAll();
    }

    @Test
    @DisplayName("같은 PENDING 메시지에 relay.poll()을 2번 호출해도 알림은 정확히 1건, 메시지는 PROCESSED로 유지된다")
    void pollTwice_createsExactlyOneNotification() {
        // given: 시니어/보호자 연동 + BREAKFAST schedule 1건이 TAKEN으로 완료된 상태
        final Long seniorId = seedSenior();
        final Long caregiverId = seedCaregiver();
        seedRelation(seniorId, caregiverId);
        final LocalDate today = LocalDate.now();
        final Long medicineId = seedMedicine(seniorId);
        final Long scheduleId = seedSchedule(medicineId, MealSlot.BREAKFAST);
        seedTakenLog(seniorId, scheduleId, today);
        // 완료 이벤트를 담은 PENDING outbox 메시지 적재 (프로덕션 enqueue 경로 그대로)
        final Long messageId = transactionTemplate.execute(status -> outboxService
                .enqueue(OutboxService.MEDICATION_COMPLETE, new MedicationTakenEvent(seniorId, today))
                .getId());

        // when: relay를 2번 폴링 (1번째: PENDING 클레임 → 발송 → PROCESSED, 2번째: 재처리 시도)
        relay.poll();
        relay.poll();

        // then: 보호자 알림은 정확히 1건 (중복 발송 없음)
        final List<Notification> notifications = findCompleteNotifications(caregiverId);
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getSeniorId()).isEqualTo(seniorId);
        assertThat(notifications.get(0).getTargetDate()).isEqualTo(today);
        assertThat(notifications.get(0).getMealSlot()).isEqualTo(MealSlot.BREAKFAST.name());

        // outbox 메시지는 PROCESSED로 종결되어 2번째 poll에서 findClaimable(PENDING만)에 잡히지 않아
        // 재디스패치 자체가 일어나지 않는다 (attempts=0 유지가 그 증거)
        final OutboxMessage message = outboxMessageRepository.findById(messageId).orElseThrow();
        assertThat(message.getStatus()).isEqualTo(OutboxStatus.PROCESSED);
        assertThat(message.getAttempts()).isEqualTo(0);
        assertThat(message.getProcessedAt()).isNotNull();
    }

    // --- helpers ---

    private List<Notification> findCompleteNotifications(final Long caregiverId) {
        return notificationRepository.findAll().stream()
                .filter(n -> n.getUserId().equals(caregiverId))
                .filter(n -> n.getCategory() == NotificationCategory.MEDICATION_COMPLETE)
                .toList();
    }

    // --- fixtures ---

    private Long seedSenior() {
        return transactionTemplate.execute(status -> {
            final User senior = User.createSenior("멱등시니어" + userSequence++, (LocalDate) null);
            senior.changeCareMode(CareMode.AUTONOMOUS);
            return userRepository.save(senior).getId();
        });
    }

    private Long seedCaregiver() {
        return transactionTemplate.execute(status -> {
            final User caregiver = User.createSenior("멱등보호자" + userSequence++, (LocalDate) null);
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
            final Medicine medicine = new Medicine(seniorId, null, "멱등테스트약", 30, 30, "ITEM-1", null);
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

    private void seedTakenLog(final Long seniorId, final Long scheduleId, final LocalDate targetDate) {
        transactionTemplate.executeWithoutResult(status -> logRepository.save(new MedicationLog(
                seniorId, scheduleId, targetDate, LocalDateTime.now(), LogStatus.TAKEN,
                null, false, seniorId)));
    }
}
