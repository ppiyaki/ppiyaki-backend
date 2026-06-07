package com.ppiyaki.notification.service;

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
import com.ppiyaki.user.domain.UserRole;
import com.ppiyaki.user.repository.CareRelationRepository;
import com.ppiyaki.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class FamilySafetyDispatcher {

    private static final Logger log = LoggerFactory.getLogger(FamilySafetyDispatcher.class);
    private static final int DEFAULT_THRESHOLD_HOURS = 48;

    private final UserRepository userRepository;
    private final CareRelationRepository careRelationRepository;
    private final NotificationSettingsRepository settingsRepository;
    private final NotificationRepository notificationRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final PushSender pushSender;
    private final Clock clock;

    public FamilySafetyDispatcher(
            final UserRepository userRepository,
            final CareRelationRepository careRelationRepository,
            final NotificationSettingsRepository settingsRepository,
            final NotificationRepository notificationRepository,
            final DeviceTokenRepository deviceTokenRepository,
            final PushSender pushSender,
            final Clock clock
    ) {
        this.userRepository = userRepository;
        this.careRelationRepository = careRelationRepository;
        this.settingsRepository = settingsRepository;
        this.notificationRepository = notificationRepository;
        this.deviceTokenRepository = deviceTokenRepository;
        this.pushSender = pushSender;
        this.clock = clock;
    }

    @Transactional
    public int run() {
        final LocalDateTime now = LocalDateTime.now(clock);
        final LocalDate today = LocalDate.now(clock);
        int dispatched = 0;
        for (final User senior : userRepository.findAllByRoleWithMealTimesSet(UserRole.SENIOR)) {
            dispatched += dispatchForSenior(senior, now, today);
        }
        return dispatched;
    }

    private int dispatchForSenior(final User senior, final LocalDateTime now, final LocalDate today) {
        final LocalDateTime lastActive = senior.getLastActiveAt();
        if (lastActive == null) {
            return 0;
        }
        final List<CareRelation> relations = careRelationRepository.findBySeniorIdAndDeletedAtIsNull(senior.getId());
        if (relations.isEmpty()) {
            return 0;
        }
        int sent = 0;
        for (final CareRelation relation : relations) {
            final Long caregiverId = relation.getCaregiverId();
            final NotificationSettings settings = settingsRepository
                    .findByCaregiverIdAndSeniorId(caregiverId, senior.getId())
                    .orElse(null);
            if (settings != null && !settings.isFamilySafetyEnabled()) {
                continue;
            }
            final int thresholdHours = settings != null
                    ? settings.getFamilySafetyThresholdHours() : DEFAULT_THRESHOLD_HOURS;
            if (now.isBefore(lastActive.plusHours(thresholdHours))) {
                continue;
            }
            // 시니어가 재접속할 때까지 1회만 (Q8) — last_active_at 갱신 후의 가장 최근 row 확인
            final var lastSent = notificationRepository
                    .findFirstByUserIdAndCategoryAndSeniorIdOrderByCreatedAtDesc(
                            caregiverId, NotificationCategory.FAMILY_SAFETY, senior.getId());
            if (lastSent.isPresent() && lastSent.get().getCreatedAt() != null
                    && lastSent.get().getCreatedAt().isAfter(lastActive)) {
                continue;
            }

            final String title = "가족 안전망 알림";
            final String body = String.format(
                    "%s님이 %d시간 이상 앱에 접속하지 않았습니다.",
                    senior.getNickname() == null ? "" : senior.getNickname(), thresholdHours);
            final Notification saved = notificationRepository.save(
                    Notification.createForFamilySafety(caregiverId, senior.getId(), title, body, today));
            log.info("FAMILY_SAFETY notification created (id={}, caregiver={}, senior={}, lastActive={})",
                    saved.getId(), caregiverId, senior.getId(), lastActive);

            final List<DeviceToken> tokens = deviceTokenRepository.findByUserIdAndIsActiveTrue(caregiverId);
            for (final DeviceToken token : tokens) {
                final PushSendResult result = pushSender.send(token.getToken(),
                        new PushPayload(title, body, Map.of(
                                "category", "FAMILY_SAFETY",
                                "seniorId", String.valueOf(senior.getId())
                        )));
                if (result.tokenInvalid()) {
                    token.deactivate();
                }
            }
            sent++;
        }
        return sent;
    }
}
