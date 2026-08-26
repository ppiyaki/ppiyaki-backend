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

            final Notification saved = notificationRepository.saveAndFlush(Notification.createForMedicationComplete(
                    caregiverId, seniorId, title, body, targetDate, slot.name()));
            log.info("MEDICATION_COMPLETE notification created (id={}, caregiver={}, senior={}, date={}, slot={})",
                    saved.getId(), caregiverId, seniorId, targetDate, slot);

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
