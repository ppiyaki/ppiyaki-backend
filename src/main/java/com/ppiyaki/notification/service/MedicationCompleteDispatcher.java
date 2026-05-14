package com.ppiyaki.notification.service;

import com.ppiyaki.infrastructure.messaging.fcm.PushPayload;
import com.ppiyaki.infrastructure.messaging.fcm.PushSendResult;
import com.ppiyaki.infrastructure.messaging.fcm.PushSender;
import com.ppiyaki.medication.LogStatus;
import com.ppiyaki.medication.MedicationLog;
import com.ppiyaki.medication.MedicationSchedule;
import com.ppiyaki.medication.repository.MedicationLogRepository;
import com.ppiyaki.medication.repository.MedicationScheduleRepository;
import com.ppiyaki.notification.DeviceToken;
import com.ppiyaki.notification.Notification;
import com.ppiyaki.notification.NotificationCategory;
import com.ppiyaki.notification.NotificationSettings;
import com.ppiyaki.notification.repository.DeviceTokenRepository;
import com.ppiyaki.notification.repository.NotificationRepository;
import com.ppiyaki.notification.repository.NotificationSettingsRepository;
import com.ppiyaki.user.CareRelation;
import com.ppiyaki.user.User;
import com.ppiyaki.user.repository.CareRelationRepository;
import com.ppiyaki.user.repository.UserRepository;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
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
    private final PushSender pushSender;

    public MedicationCompleteDispatcher(
            final UserRepository userRepository,
            final CareRelationRepository careRelationRepository,
            final MedicationScheduleRepository scheduleRepository,
            final MedicationLogRepository logRepository,
            final NotificationSettingsRepository settingsRepository,
            final NotificationRepository notificationRepository,
            final DeviceTokenRepository deviceTokenRepository,
            final PushSender pushSender
    ) {
        this.userRepository = userRepository;
        this.careRelationRepository = careRelationRepository;
        this.scheduleRepository = scheduleRepository;
        this.logRepository = logRepository;
        this.settingsRepository = settingsRepository;
        this.notificationRepository = notificationRepository;
        this.deviceTokenRepository = deviceTokenRepository;
        this.pushSender = pushSender;
    }

    @Transactional
    public int dispatchIfDayComplete(final Long seniorId, final LocalDate targetDate) {
        final List<MedicationSchedule> schedules = scheduleRepository.findActiveByOwnerAndDate(seniorId, targetDate);
        if (schedules.isEmpty()) {
            return 0;
        }
        final Set<Long> takenScheduleIds = new HashSet<>();
        for (final MedicationLog logRow : logRepository.findBySeniorIdAndTargetDate(seniorId, targetDate)) {
            if (logRow.getStatus() == LogStatus.TAKEN) {
                takenScheduleIds.add(logRow.getScheduleId());
            }
        }
        for (final MedicationSchedule schedule : schedules) {
            if (!takenScheduleIds.contains(schedule.getId())) {
                return 0;
            }
        }

        final List<CareRelation> relations = careRelationRepository.findBySeniorIdAndDeletedAtIsNull(seniorId);
        if (relations.isEmpty()) {
            return 0;
        }
        final User senior = userRepository.findById(seniorId).orElse(null);
        if (senior == null) {
            return 0;
        }

        final String title = "복약 완료 알림";
        final String body = String.format(
                "축하합니다! %s 어르신이 모든 복약을 완료하셨습니다!",
                senior.getNickname() == null ? "" : senior.getNickname());

        int dispatched = 0;
        for (final CareRelation relation : relations) {
            final Long caregiverId = relation.getCaregiverId();
            final NotificationSettings settings = settingsRepository
                    .findByCaregiverIdAndSeniorId(caregiverId, seniorId)
                    .orElse(null);
            if (settings != null && !settings.isMedicationCompleteEnabled()) {
                continue;
            }
            if (notificationRepository.existsByUserIdAndCategoryAndSeniorIdAndTargetDate(
                    caregiverId, NotificationCategory.MEDICATION_COMPLETE, seniorId, targetDate)) {
                continue;
            }
            final Notification saved = notificationRepository.save(
                    Notification.createForMedicationComplete(caregiverId, seniorId, title, body, targetDate));
            log.info("MEDICATION_COMPLETE notification created (id={}, caregiver={}, senior={}, date={})",
                    saved.getId(), caregiverId, seniorId, targetDate);

            final List<DeviceToken> tokens = deviceTokenRepository.findByUserIdAndIsActiveTrue(caregiverId);
            for (final DeviceToken token : tokens) {
                final PushSendResult result = pushSender.send(token.getToken(),
                        new PushPayload(title, body, Map.of(
                                "category", "MEDICATION_COMPLETE",
                                "seniorId", String.valueOf(seniorId),
                                "targetDate", targetDate.toString()
                        )));
                if (result.tokenInvalid()) {
                    token.deactivate();
                }
            }
            dispatched++;
        }
        return dispatched;
    }
}
