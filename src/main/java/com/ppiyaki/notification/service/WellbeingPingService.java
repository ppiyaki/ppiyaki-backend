package com.ppiyaki.notification.service;

import com.ppiyaki.common.exception.BusinessException;
import com.ppiyaki.common.exception.ErrorCode;
import com.ppiyaki.infrastructure.messaging.fcm.PushPayload;
import com.ppiyaki.infrastructure.messaging.fcm.PushSendResult;
import com.ppiyaki.infrastructure.messaging.fcm.PushSender;
import com.ppiyaki.notification.DeviceToken;
import com.ppiyaki.notification.NotificationCategory;
import com.ppiyaki.notification.repository.DeviceTokenRepository;
import com.ppiyaki.user.domain.User;
import com.ppiyaki.user.domain.UserRole;
import com.ppiyaki.user.repository.CareRelationRepository;
import com.ppiyaki.user.repository.UserRepository;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WellbeingPingService {

    private static final Logger log = LoggerFactory.getLogger(WellbeingPingService.class);
    private static final String PUSH_TITLE = "안부 알림";
    private static final String PUSH_BODY_FORMAT = "%s님이 안부를 전했어요.";

    private final UserRepository userRepository;
    private final CareRelationRepository careRelationRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final WellbeingPingCooldownStore cooldownStore;
    private final PushSender pushSender;

    public WellbeingPingService(
            final UserRepository userRepository,
            final CareRelationRepository careRelationRepository,
            final DeviceTokenRepository deviceTokenRepository,
            final WellbeingPingCooldownStore cooldownStore,
            final PushSender pushSender
    ) {
        this.userRepository = userRepository;
        this.careRelationRepository = careRelationRepository;
        this.deviceTokenRepository = deviceTokenRepository;
        this.cooldownStore = cooldownStore;
        this.pushSender = pushSender;
    }

    @Transactional
    public void send(final Long seniorId, final Long caregiverId) {
        final User senior = userRepository.findById(seniorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (senior.getRole() != UserRole.SENIOR) {
            throw new BusinessException(ErrorCode.CARE_RELATION_ROLE_MISMATCH);
        }

        final User caregiver = userRepository.findById(caregiverId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (caregiver.getRole() != UserRole.CAREGIVER) {
            throw new BusinessException(ErrorCode.CARE_RELATION_ROLE_MISMATCH);
        }

        if (careRelationRepository
                .findByCaregiverIdAndSeniorIdAndDeletedAtIsNull(caregiverId, seniorId)
                .isEmpty()) {
            throw new BusinessException(ErrorCode.CARE_RELATION_NOT_FOUND);
        }

        if (!cooldownStore.tryAcquire(seniorId, caregiverId)) {
            final long retryAfterSeconds = cooldownStore
                    .getRetryAfterSeconds(seniorId, caregiverId)
                    .orElse(1L);
            log.debug("WELLBEING_PING cooldown blocked (sender={}, receiver={}, retryAfter={}s)",
                    seniorId, caregiverId, retryAfterSeconds);
            throw new WellbeingPingCooldownException(retryAfterSeconds);
        }

        final List<DeviceToken> tokens = deviceTokenRepository.findByUserIdAndIsActiveTrue(caregiverId);
        final String body = PUSH_BODY_FORMAT.formatted(senior.getNickname() == null ? "" : senior.getNickname());
        final PushPayload payload = new PushPayload(PUSH_TITLE, body, Map.of(
                "category", NotificationCategory.WELLBEING_PING.name(),
                "seniorId", String.valueOf(seniorId)
        ));

        for (final DeviceToken token : tokens) {
            final PushSendResult result = pushSender.send(token.getToken(), payload);
            if (result.tokenInvalid()) {
                token.deactivate();
            }
        }
        log.info("WELLBEING_PING dispatched (sender={}, receiver={}, tokens={})",
                seniorId, caregiverId, tokens.size());
    }
}
