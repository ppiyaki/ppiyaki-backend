package com.ppiyaki.notification.service;

import com.ppiyaki.medication.LogStatus;
import com.ppiyaki.medication.MealSlot;
import com.ppiyaki.medication.MedicationLog;
import com.ppiyaki.medication.MedicationSchedule;
import com.ppiyaki.medication.repository.MedicationLogRepository;
import com.ppiyaki.medication.repository.MedicationScheduleRepository;
import com.ppiyaki.notification.DeviceToken;
import com.ppiyaki.notification.Notification;
import com.ppiyaki.notification.NotificationCategory;
import com.ppiyaki.notification.NotificationSettings;
import com.ppiyaki.notification.push.PushPayload;
import com.ppiyaki.notification.push.PushSendResult;
import com.ppiyaki.notification.push.PushSender;
import com.ppiyaki.notification.repository.DeviceTokenRepository;
import com.ppiyaki.notification.repository.NotificationRepository;
import com.ppiyaki.notification.repository.NotificationSettingsRepository;
import com.ppiyaki.user.CareRelation;
import com.ppiyaki.user.User;
import com.ppiyaki.user.repository.CareRelationRepository;
import com.ppiyaki.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class MedicationDelayDispatcher {

    private static final Logger log = LoggerFactory.getLogger(MedicationDelayDispatcher.class);
    private static final Map<MealSlot, String> SLOT_LABELS = Map.of(
            MealSlot.BREAKFAST, "아침",
            MealSlot.LUNCH, "점심",
            MealSlot.DINNER, "저녁"
    );

    private final UserRepository userRepository;
    private final CareRelationRepository careRelationRepository;
    private final MedicationScheduleRepository scheduleRepository;
    private final MedicationLogRepository logRepository;
    private final NotificationSettingsRepository settingsRepository;
    private final NotificationRepository notificationRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final PushSender pushSender;
    private final Clock clock;

    public MedicationDelayDispatcher(
            final UserRepository userRepository,
            final CareRelationRepository careRelationRepository,
            final MedicationScheduleRepository scheduleRepository,
            final MedicationLogRepository logRepository,
            final NotificationSettingsRepository settingsRepository,
            final NotificationRepository notificationRepository,
            final DeviceTokenRepository deviceTokenRepository,
            final PushSender pushSender,
            final Clock clock
    ) {
        this.userRepository = userRepository;
        this.careRelationRepository = careRelationRepository;
        this.scheduleRepository = scheduleRepository;
        this.logRepository = logRepository;
        this.settingsRepository = settingsRepository;
        this.notificationRepository = notificationRepository;
        this.deviceTokenRepository = deviceTokenRepository;
        this.pushSender = pushSender;
        this.clock = clock;
    }

    @Transactional
    public int dispatchForSenior(final User senior, final LocalDate today) {
        int dispatched = 0;
        final List<CareRelation> relations = careRelationRepository.findBySeniorIdAndDeletedAtIsNull(senior.getId());
        if (relations.isEmpty()) {
            return 0;
        }
        for (final MealSlot slot : MealSlot.values()) {
            final LocalTime mealTime = slot.resolveTime(senior);
            if (mealTime == null) {
                continue;
            }
            final LocalDateTime mealDateTime = LocalDateTime.of(today, mealTime);
            final LocalDateTime now = LocalDateTime.now(clock);
            if (now.isBefore(mealDateTime)) {
                continue;
            }

            final List<MedicationSchedule> slotSchedules = scheduleRepository.findActiveByOwnerAndMealSlot(senior
                    .getId(), today, slot);
            if (slotSchedules.isEmpty()) {
                continue;
            }
            final Set<Long> takenScheduleIds = new HashSet<>();
            for (final MedicationLog logRow : logRepository.findBySeniorIdAndTargetDate(senior.getId(), today)) {
                if (logRow.getStatus() == LogStatus.TAKEN) {
                    takenScheduleIds.add(logRow.getScheduleId());
                }
            }

            for (final CareRelation relation : relations) {
                final Long caregiverId = relation.getCaregiverId();
                final NotificationSettings settings = settingsRepository
                        .findByCaregiverIdAndSeniorId(caregiverId, senior.getId())
                        .orElse(null);
                if (settings != null && !settings.isMedicationDelayEnabled()) {
                    continue;
                }
                final long thresholdMinutes = settings != null
                        ? settings.getMedicationDelayThresholdMinutes() : 60L;
                if (now.isBefore(mealDateTime.plusMinutes(thresholdMinutes))) {
                    continue;
                }
                for (final MedicationSchedule schedule : slotSchedules) {
                    if (takenScheduleIds.contains(schedule.getId())) {
                        continue;
                    }
                    if (notificationRepository.existsByUserIdAndCategoryAndSeniorIdAndTargetDateAndScheduleId(
                            caregiverId, NotificationCategory.MEDICATION_DELAY, senior.getId(), today,
                            schedule.getId())) {
                        continue;
                    }
                    sendDelayNotification(caregiverId, senior, slot, today, schedule, thresholdMinutes);
                    dispatched++;
                }
            }
        }
        return dispatched;
    }

    private void sendDelayNotification(
            final Long caregiverId,
            final User senior,
            final MealSlot slot,
            final LocalDate today,
            final MedicationSchedule schedule,
            final long thresholdMinutes
    ) {
        final String slotLabel = SLOT_LABELS.getOrDefault(slot, slot.name());
        final String title = "복약 지연 알림";
        final String body = String.format(
                "%s 어르신이 %s 약 복용 시간을 %d분 넘겼습니다.",
                senior.getNickname() == null ? "" : senior.getNickname(),
                slotLabel, thresholdMinutes);
        final Notification saved = notificationRepository.save(
                Notification.createForMedicationDelay(
                        caregiverId, senior.getId(), title, body, today, slot.name(), schedule.getId()));
        log.info("MEDICATION_DELAY notification created (id={}, caregiver={}, senior={}, slot={}, schedule={})",
                saved.getId(), caregiverId, senior.getId(), slot, schedule.getId());

        final List<DeviceToken> tokens = deviceTokenRepository.findByUserIdAndIsActiveTrue(caregiverId);
        for (final DeviceToken token : tokens) {
            final PushSendResult result = pushSender.send(token.getToken(),
                    new PushPayload(title, body, Map.of(
                            "category", "MEDICATION_DELAY",
                            "seniorId", String.valueOf(senior.getId()),
                            "scheduleId", String.valueOf(schedule.getId()),
                            "mealSlot", slot.name()
                    )));
            if (result.tokenInvalid()) {
                token.deactivate();
            }
        }
    }

    public Iterable<User> findAllSeniorsWithMealTimes() {
        return userRepository.findAllByRoleWithMealTimesSet(com.ppiyaki.user.UserRole.SENIOR);
    }
}
