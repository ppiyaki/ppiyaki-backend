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
import com.ppiyaki.prescription.event.PrescriptionReviewRequestedEvent;
import com.ppiyaki.user.domain.CareMode;
import com.ppiyaki.user.domain.CareRelation;
import com.ppiyaki.user.domain.User;
import com.ppiyaki.user.domain.UserRole;
import com.ppiyaki.user.repository.CareRelationRepository;
import com.ppiyaki.user.repository.UserRepository;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class PrescriptionReviewRequestDispatcher {

    private static final Logger log = LoggerFactory.getLogger(PrescriptionReviewRequestDispatcher.class);
    private static final String PUSH_TITLE = "처방전 검토 요청";
    private static final String PUSH_BODY_FORMAT = "%s 어르신의 새 처방전이 도착했어요. 검토해 주세요.";

    private final UserRepository userRepository;
    private final CareRelationRepository careRelationRepository;
    private final NotificationSettingsRepository settingsRepository;
    private final NotificationRepository notificationRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final PushSender pushSender;

    public PrescriptionReviewRequestDispatcher(
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

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(final PrescriptionReviewRequestedEvent event) {
        final Long seniorId = event.seniorId();
        final Long prescriptionId = event.prescriptionId();

        final User senior = userRepository.findById(seniorId).orElse(null);
        if (senior == null || senior.getRole() != UserRole.SENIOR) {
            return;
        }
        if (senior.getCareMode() != CareMode.MANAGED) {
            return;
        }

        final List<CareRelation> relations = careRelationRepository.findBySeniorIdAndDeletedAtIsNull(seniorId);
        if (relations.isEmpty()) {
            return;
        }

        final String body = PUSH_BODY_FORMAT.formatted(senior.getNickname() == null ? "" : senior.getNickname());
        int dispatched = 0;
        for (final CareRelation relation : relations) {
            final Long caregiverId = relation.getCaregiverId();
            final NotificationSettings settings = settingsRepository
                    .findByCaregiverIdAndSeniorId(caregiverId, seniorId)
                    .orElse(null);
            if (settings != null && !settings.isPrescriptionReviewRequestEnabled()) {
                continue;
            }

            notificationRepository.save(
                    Notification.createForPrescriptionReviewRequest(caregiverId, seniorId, PUSH_TITLE, body));

            final List<DeviceToken> tokens = deviceTokenRepository.findByUserIdAndIsActiveTrue(caregiverId);
            for (final DeviceToken token : tokens) {
                final PushSendResult result = pushSender.send(token.getToken(),
                        new PushPayload(PUSH_TITLE, body, Map.of(
                                "category", NotificationCategory.PRESCRIPTION_REVIEW_REQUEST.name(),
                                "seniorId", String.valueOf(seniorId),
                                "prescriptionId", String.valueOf(prescriptionId)
                        )));
                if (result.tokenInvalid()) {
                    token.deactivate();
                }
            }
            dispatched++;
        }
        log.info("PRESCRIPTION_REVIEW_REQUEST dispatched (seniorId={}, prescriptionId={}, recipients={})",
                seniorId, prescriptionId, dispatched);
    }
}
