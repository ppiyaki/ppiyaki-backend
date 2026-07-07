package com.ppiyaki.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ppiyaki.infrastructure.messaging.fcm.PushPayload;
import com.ppiyaki.infrastructure.messaging.fcm.PushSendResult;
import com.ppiyaki.infrastructure.messaging.fcm.PushSender;
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
import com.ppiyaki.notification.DevicePlatform;
import com.ppiyaki.notification.DeviceToken;
import com.ppiyaki.notification.Notification;
import com.ppiyaki.notification.NotificationCategory;
import com.ppiyaki.notification.repository.DeviceTokenRepository;
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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "openai.api-key=sk-test-placeholder",
        "openai.model=gpt-test",
        "ncp.storage.endpoint=https://kr.object.ncloudstorage.com",
        "ncp.storage.region=kr-standard",
        "ncp.storage.access-key=test-access-key",
        "ncp.storage.secret-key=test-secret-key",
        "ncp.storage.bucket-name=ppiyaki-test",

        "outbox.relay.initial-delay-ms=3600000"
})
@DisplayName("복약 완료 Outbox relay: record 내구성(tx 안) / FCM 푸시(커밋 후 best-effort) 경계")
class MedicationCompleteOutboxRelayPushBoundaryTest {

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
    private DeviceTokenRepository deviceTokenRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private PushSender pushSender;

    private static long userSequence = 930000L;

    @BeforeEach
    void setUp() {

        outboxMessageRepository.deleteAll();
    }

    @Test
    @DisplayName("(a) FCM 발송이 예외로 실패해도 알림함 record는 저장되고 메시지는 PROCESSED, 푸시는 best-effort")
    void pushFailure_doesNotAffectRecordDurabilityNorOutboxStatus() {

        final Fixture fx = seedCompletedBreakfast();
        seedActiveToken(fx.caregiverId());
        when(pushSender.send(anyString(), any(PushPayload.class)))
                .thenThrow(new RuntimeException("FCM down 시뮬레이션"));
        final Long messageId = enqueue(fx.seniorId(), fx.today());

        relay.poll();

        assertThat(findCompleteNotifications(fx.caregiverId())).hasSize(1);
        final OutboxMessage message = outboxMessageRepository.findById(messageId).orElseThrow();
        assertThat(message.getStatus()).isEqualTo(OutboxStatus.PROCESSED);
        assertThat(message.getAttempts()).isEqualTo(0);
        assertThat(message.getLastError()).isNull();

        verify(pushSender, times(1)).send(anyString(), any(PushPayload.class));
    }

    @Test
    @DisplayName("(b) record 저장은 워커 트랜잭션 안, FCM 호출은 커밋 이후. send 시점에 활성 tx가 없고 별도 커넥션에서 record/PROCESSED가 보인다")
    void pushIsSentAfterCommit_recordIsSavedInsideTransaction() {

        final Fixture fx = seedCompletedBreakfast();
        seedActiveToken(fx.caregiverId());
        final Long messageId = enqueue(fx.seniorId(), fx.today());

        final AtomicReference<Boolean> txActiveAtSend = new AtomicReference<>();
        final AtomicReference<Integer> committedNotificationsAtSend = new AtomicReference<>();
        final AtomicReference<String> outboxStatusAtSend = new AtomicReference<>();
        when(pushSender.send(anyString(), any(PushPayload.class))).thenAnswer(invocation -> {
            txActiveAtSend.set(TransactionSynchronizationManager.isActualTransactionActive());
            committedNotificationsAtSend.set(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND category = ?",
                    Integer.class, fx.caregiverId(), NotificationCategory.MEDICATION_COMPLETE.name()));
            outboxStatusAtSend.set(jdbcTemplate.queryForObject(
                    "SELECT status FROM outbox_message WHERE id = ?", String.class, messageId));
            return PushSendResult.ok();
        });

        relay.poll();

        verify(pushSender, times(1)).send(anyString(), any(PushPayload.class));

        assertThat(txActiveAtSend.get()).isFalse();

        assertThat(committedNotificationsAtSend.get()).isEqualTo(1);
        assertThat(outboxStatusAtSend.get()).isEqualTo(OutboxStatus.PROCESSED.name());
    }

    @Test
    @DisplayName("(c) 발송 결과 invalid 토큰만 별도 트랜잭션에서 비활성화되고, 정상 토큰은 활성 유지된다")
    void invalidToken_isDeactivatedInSeparateTransaction() {

        final Fixture fx = seedCompletedBreakfast();
        final DeviceToken invalidToken = seedActiveToken(fx.caregiverId());
        final DeviceToken healthyToken = seedActiveToken(fx.caregiverId());
        when(pushSender.send(anyString(), any(PushPayload.class))).thenAnswer(invocation -> {
            final String token = invocation.getArgument(0);
            return token.equals(invalidToken.getToken())
                    ? PushSendResult.tokenInvalid("UNREGISTERED")
                    : PushSendResult.ok();
        });
        enqueue(fx.seniorId(), fx.today());

        relay.poll();

        assertThat(deviceTokenRepository.findById(invalidToken.getId()).orElseThrow().getIsActive()).isFalse();
        assertThat(deviceTokenRepository.findById(healthyToken.getId()).orElseThrow().getIsActive()).isTrue();

        assertThat(findCompleteNotifications(fx.caregiverId())).hasSize(1);
    }

    private Long enqueue(final Long seniorId, final LocalDate targetDate) {
        return transactionTemplate.execute(status -> outboxService
                .enqueue(OutboxService.MEDICATION_COMPLETE, new MedicationTakenEvent(seniorId, targetDate))
                .getId());
    }

    private List<Notification> findCompleteNotifications(final Long caregiverId) {
        return notificationRepository.findAll().stream()
                .filter(n -> n.getUserId().equals(caregiverId))
                .filter(n -> n.getCategory() == NotificationCategory.MEDICATION_COMPLETE)
                .toList();
    }

    private DeviceToken seedActiveToken(final Long caregiverId) {
        return transactionTemplate.execute(status -> deviceTokenRepository.save(
                DeviceToken.register(caregiverId, "push-boundary-" + UUID.randomUUID(), DevicePlatform.ANDROID)));
    }

    private record Fixture(Long seniorId, Long caregiverId, LocalDate today) {
    }

    private Fixture seedCompletedBreakfast() {
        final Long seniorId = seedSenior();
        final Long caregiverId = seedCaregiver();
        seedRelation(seniorId, caregiverId);
        final LocalDate today = LocalDate.now();
        final Long medicineId = seedMedicine(seniorId);
        final Long scheduleId = seedSchedule(medicineId, MealSlot.BREAKFAST);
        seedTakenLog(seniorId, scheduleId, today);
        return new Fixture(seniorId, caregiverId, today);
    }

    private Long seedSenior() {
        return transactionTemplate.execute(status -> {
            final User senior = User.createSenior("경계시니어" + userSequence++, (LocalDate) null);
            senior.changeCareMode(CareMode.AUTONOMOUS);
            return userRepository.save(senior).getId();
        });
    }

    private Long seedCaregiver() {
        return transactionTemplate.execute(status -> {
            final User caregiver = User.createSenior("경계보호자" + userSequence++, (LocalDate) null);
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
            final Medicine medicine = new Medicine(seniorId, null, "경계테스트약", 30, 30, "ITEM-1", null);
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
