package com.ppiyaki.notification.service;

import com.ppiyaki.infrastructure.messaging.fcm.PushPayload;
import com.ppiyaki.medication.domain.LogStatus;
import com.ppiyaki.medication.domain.MealSlot;
import com.ppiyaki.medication.domain.MedicationLog;
import com.ppiyaki.medication.domain.MedicationSchedule;
import com.ppiyaki.medication.repository.MedicationLogRepository;
import com.ppiyaki.medication.repository.MedicationScheduleRepository;
import com.ppiyaki.notification.DeviceToken;
import com.ppiyaki.notification.Notification;
import com.ppiyaki.notification.NotificationCategory;
import com.ppiyaki.notification.NotificationSettings;
import com.ppiyaki.notification.repository.DeviceTokenRepository;
import com.ppiyaki.notification.repository.NotificationRepository;
import com.ppiyaki.notification.repository.NotificationSettingsRepository;
import com.ppiyaki.user.domain.CareRelation;
import com.ppiyaki.user.domain.User;
import com.ppiyaki.user.repository.CareRelationRepository;
import com.ppiyaki.user.repository.UserRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class MedicationCompleteDispatcher {

    private static final Logger log = LoggerFactory.getLogger(MedicationCompleteDispatcher.class);

    private final UserRepository userRepository;
    private final CareRelationRepository careRelationRepository;
    private final MedicationScheduleRepository scheduleRepository;
    private final MedicationLogRepository logRepository;
    private final NotificationSettingsRepository settingsRepository;
    private final NotificationRepository notificationRepository;
    private final DeviceTokenRepository deviceTokenRepository;

    public MedicationCompleteDispatcher(
            final UserRepository userRepository,
            final CareRelationRepository careRelationRepository,
            final MedicationScheduleRepository scheduleRepository,
            final MedicationLogRepository logRepository,
            final NotificationSettingsRepository settingsRepository,
            final NotificationRepository notificationRepository,
            final DeviceTokenRepository deviceTokenRepository
    ) {
        this.userRepository = userRepository;
        this.careRelationRepository = careRelationRepository;
        this.scheduleRepository = scheduleRepository;
        this.logRepository = logRepository;
        this.settingsRepository = settingsRepository;
        this.notificationRepository = notificationRepository;
        this.deviceTokenRepository = deviceTokenRepository;
    }

    /**
     * 끼니(아침/점심/저녁)별로 그 끼니에 속한 모든 schedule이 인증되면 보호자 알림함에
     * MEDICATION_COMPLETE record를 저장하고, 커밋 이후 발송할 FCM 푸시 명령 목록을 반환한다.
     *
     * <p><b>전파: REQUIRES_NEW (poison-batch 격리)</b>. Outbox relay
     * ({@code MedicationCompleteOutboxRelay.processBatch})의 클레임 트랜잭션에서 호출되지만,
     * 합류(REQUIRED)하지 않고 메시지 단위의 새 트랜잭션을 연다. 합류하면 이 메서드의
     * RuntimeException이 참여 트랜잭션을 rollback-only로 마킹해
     * (globalRollbackOnParticipationFailure) relay의 catch + recordFailure가 무력화되고,
     * processBatch 커밋 시 UnexpectedRollbackException으로 배치 전체가 롤백되어
     * attempts 증가/백오프/데드레터가 전부 동작하지 않기 때문이다. REQUIRES_NEW에서는
     * 실패가 이 내부 트랜잭션만 롤백시키고 예외로 전파되므로, relay의 외부(클레임) 트랜잭션이
     * recordFailure를 정상 커밋할 수 있다. 유니크 제약 위반이 커밋 시점이 아니라 이 트랜잭션
     * 경계 안에서 표면화되도록 record 저장은 {@code saveAndFlush}로 즉시 flush한다.
     *
     * <p><b>비용</b>: dispatch가 진행되는 동안 relay 스레드가 DB 커넥션 2개를 점유한다
     * (외부 클레임 트랜잭션 1개 + 이 REQUIRES_NEW 트랜잭션 1개). 커넥션 풀 사이즈 산정 시 고려할 것.
     *
     * <p><b>부분 커밋 주의</b>: 이 메서드가 성공하면 알림 record는 그 시점에 커밋된다. 이후 relay의
     * 외부 트랜잭션이 롤백되어 메시지가 PENDING으로 남더라도, 재처리 시 exists 선체크 + 유니크 제약으로
     * record는 중복 저장되지 않고, 푸시 명령은 새로 저장된 record에 대해서만 만들어지므로 중복 푸시도 없다.
     *
     * <p><b>트랜잭션 경계</b>: 이 메서드는 <b>알림함 record 저장까지만</b> 트랜잭션 안에서 수행한다.
     * 외부 네트워크 호출인 FCM 발송은 DB 커넥션/트랜잭션을 점유하지 않도록 여기서 하지 않고,
     * 발송에 필요한 정보를 {@link PushCommand}로 반환해 relay가 <b>커밋 이후 best-effort</b>로 발송한다.
     * 푸시 명령은 dedup(exists 선체크 + 유니크 제약)을 통과해 <b>새로 저장된 record</b>에 대해서만
     * 만들어지므로, relay 재실행 시 중복 푸시가 최소화된다.
     *
     * @return 커밋 이후 발송할 푸시 명령 목록 (발송 대상이 없으면 빈 리스트)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<PushCommand> dispatchCompletedSlots(final Long seniorId, final LocalDate targetDate) {
        final List<MedicationSchedule> schedules = scheduleRepository.findActiveByOwnerAndDate(seniorId, targetDate);
        if (schedules.isEmpty()) {
            return List.of();
        }
        final Set<Long> takenScheduleIds = new HashSet<>();
        for (final MedicationLog logRow : logRepository.findBySeniorIdAndTargetDate(seniorId, targetDate)) {
            if (logRow.getStatus() == LogStatus.TAKEN) {
                takenScheduleIds.add(logRow.getScheduleId());
            }
        }

        final Map<MealSlot, List<MedicationSchedule>> schedulesBySlot = schedules.stream()
                .collect(Collectors.groupingBy(MedicationSchedule::getMealSlot));
        final List<MealSlot> completedSlots = new ArrayList<>();
        for (final Map.Entry<MealSlot, List<MedicationSchedule>> entry : schedulesBySlot.entrySet()) {
            final boolean slotComplete = entry.getValue().stream()
                    .allMatch(schedule -> takenScheduleIds.contains(schedule.getId()));
            if (slotComplete) {
                completedSlots.add(entry.getKey());
            }
        }
        if (completedSlots.isEmpty()) {
            return List.of();
        }

        final List<CareRelation> relations = careRelationRepository.findBySeniorIdAndDeletedAtIsNull(seniorId);
        if (relations.isEmpty()) {
            return List.of();
        }
        final User senior = userRepository.findById(seniorId).orElse(null);
        if (senior == null) {
            return List.of();
        }
        final String seniorName = senior.getNickname() == null ? "" : senior.getNickname();

        final List<PushCommand> pushCommands = new ArrayList<>();
        for (final MealSlot slot : completedSlots) {
            collectForSlot(seniorId, targetDate, slot, seniorName, relations, pushCommands);
        }
        return pushCommands;
    }

    private void collectForSlot(
            final Long seniorId,
            final LocalDate targetDate,
            final MealSlot slot,
            final String seniorName,
            final List<CareRelation> relations,
            final List<PushCommand> pushCommands
    ) {
        final String title = "복약 완료 알림";
        final String body = String.format(
                "%s님이 %s 복약을 완료했어요", seniorName, slotLabel(slot));

        for (final CareRelation relation : relations) {
            final Long caregiverId = relation.getCaregiverId();
            final NotificationSettings settings = settingsRepository
                    .findByCaregiverIdAndSeniorId(caregiverId, seniorId)
                    .orElse(null);
            if (settings != null && !settings.isMedicationCompleteEnabled()) {
                continue;
            }
            if (notificationRepository.existsByUserIdAndCategoryAndSeniorIdAndTargetDateAndMealSlot(
                    caregiverId, NotificationCategory.MEDICATION_COMPLETE, seniorId, targetDate, slot.name())) {
                continue;
            }
            // saveAndFlush: 유니크 제약 위반(동시 relay 경쟁 등)이 커밋 시점이 아니라
            // 이 REQUIRES_NEW 트랜잭션 안에서 즉시 표면화되어 relay의 recordFailure 경로를 타게 한다.
            final Notification saved = notificationRepository.saveAndFlush(Notification.createForMedicationComplete(
                    caregiverId, seniorId, title, body, targetDate, slot.name()));
            log.info("MEDICATION_COMPLETE notification created (id={}, caregiver={}, senior={}, date={}, slot={})",
                    saved.getId(), caregiverId, seniorId, targetDate, slot);

            // 새로 저장된 record에 대해서만 푸시 명령 생성 (dedup으로 skip된 건은 푸시도 없음 — 중복 푸시 최소화)
            final PushPayload payload = new PushPayload(title, body, Map.of(
                    "category", NotificationCategory.MEDICATION_COMPLETE.name(),
                    "seniorId", String.valueOf(seniorId),
                    "targetDate", targetDate.toString(),
                    "mealSlot", slot.name()
            ));
            final List<DeviceToken> tokens = deviceTokenRepository.findByUserIdAndIsActiveTrue(caregiverId);
            for (final DeviceToken token : tokens) {
                pushCommands.add(new PushCommand(token.getId(), token.getToken(), caregiverId, payload));
            }
        }
    }

    private String slotLabel(final MealSlot slot) {
        return switch (slot) {
            case BREAKFAST -> "아침";
            case LUNCH -> "점심";
            case DINNER -> "저녁";
        };
    }
}
