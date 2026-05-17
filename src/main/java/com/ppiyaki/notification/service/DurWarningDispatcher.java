package com.ppiyaki.notification.service;

import com.ppiyaki.health.DurWarningLevel;
import com.ppiyaki.infrastructure.messaging.fcm.PushPayload;
import com.ppiyaki.infrastructure.messaging.fcm.PushSendResult;
import com.ppiyaki.infrastructure.messaging.fcm.PushSender;
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
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DurWarningDispatcher {

    private static final Logger log = LoggerFactory.getLogger(DurWarningDispatcher.class);

    private final UserRepository userRepository;
    private final CareRelationRepository careRelationRepository;
    private final NotificationSettingsRepository settingsRepository;
    private final NotificationRepository notificationRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final PushSender pushSender;

    public DurWarningDispatcher(
            final UserRepository userRepository,
            final CareRelationRepository careRelationRepository,
            final NotificationSettingsRepository settingsRepository,
            final NotificationRepository notificationRepository,
            final DeviceTokenRepository deviceTokenRepository,
            final PushSender pushSender
    ) {
        this.userRepository = userRepository;
        this.careRelationRepository = careRelationRepository;
        this.settingsRepository = settingsRepository;
        this.notificationRepository = notificationRepository;
        this.deviceTokenRepository = deviceTokenRepository;
        this.pushSender = pushSender;
    }

    @Transactional
    public int dispatch(final Long seniorId, final Long medicineId, final DurWarningLevel level) {
        if (level != DurWarningLevel.WARN && level != DurWarningLevel.BLOCK) {
            return 0;
        }
        final List<CareRelation> relations = careRelationRepository.findBySeniorIdAndDeletedAtIsNull(seniorId);
        if (relations.isEmpty()) {
            return 0;
        }
        final User senior = userRepository.findById(seniorId).orElse(null);
        if (senior == null) {
            return 0;
        }

        final String title = "처방전 안전 경고";
        final String body = String.format(
                "%s 어르신의 처방전에 %s 주의 약물이 포함되어 있습니다.",
                senior.getNickname() == null ? "" : senior.getNickname(),
                level == DurWarningLevel.BLOCK ? "복용 금기" : "주의 필요");

        int dispatched = 0;
        for (final CareRelation relation : relations) {
            final Long caregiverId = relation.getCaregiverId();
            final NotificationSettings settings = settingsRepository
                    .findByCaregiverIdAndSeniorId(caregiverId, seniorId)
                    .orElse(null);
            if (settings != null && !settings.isDurWarningEnabled()) {
                continue;
            }
            if (notificationRepository.existsByUserIdAndCategoryAndSeniorIdAndTargetDateAndScheduleId(
                    caregiverId, NotificationCategory.DUR_WARNING, seniorId, null, medicineId)) {
                continue;
            }

            final Notification saved = notificationRepository.save(
                    Notification.createForDurWarning(caregiverId, seniorId, title, body, medicineId));
            log.info("DUR_WARNING notification created (id={}, caregiver={}, senior={}, medicine={}, level={})",
                    saved.getId(), caregiverId, seniorId, medicineId, level);

            final List<DeviceToken> tokens = deviceTokenRepository.findByUserIdAndIsActiveTrue(caregiverId);
            for (final DeviceToken token : tokens) {
                final PushSendResult result = pushSender.send(token.getToken(),
                        new PushPayload(title, body, Map.of(
                                "category", "DUR_WARNING",
                                "seniorId", String.valueOf(seniorId),
                                "medicineId", String.valueOf(medicineId),
                                "level", level.name()
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
