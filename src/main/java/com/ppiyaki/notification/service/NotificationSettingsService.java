package com.ppiyaki.notification.service;

import com.ppiyaki.common.exception.BusinessException;
import com.ppiyaki.common.exception.ErrorCode;
import com.ppiyaki.notification.NotificationSettings;
import com.ppiyaki.notification.controller.dto.NotificationSettingsResponse;
import com.ppiyaki.notification.controller.dto.NotificationSettingsUpdateRequest;
import com.ppiyaki.notification.repository.NotificationSettingsRepository;
import com.ppiyaki.user.domain.CareMode;
import com.ppiyaki.user.repository.CareRelationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationSettingsService {

    private final NotificationSettingsRepository notificationSettingsRepository;
    private final CareRelationRepository careRelationRepository;

    public NotificationSettingsService(
            final NotificationSettingsRepository notificationSettingsRepository,
            final CareRelationRepository careRelationRepository
    ) {
        this.notificationSettingsRepository = notificationSettingsRepository;
        this.careRelationRepository = careRelationRepository;
    }

    @Transactional
    public NotificationSettingsResponse read(final Long caregiverId, final Long seniorId) {
        validateCaregiverAccess(caregiverId, seniorId);
        final NotificationSettings settings = findOrCreate(caregiverId, seniorId);
        return NotificationSettingsResponse.from(settings);
    }

    @Transactional
    public NotificationSettingsResponse update(
            final Long caregiverId,
            final Long seniorId,
            final NotificationSettingsUpdateRequest request
    ) {
        validateCaregiverAccess(caregiverId, seniorId);
        final NotificationSettings settings = findOrCreate(caregiverId, seniorId);
        settings.updateAllFields(
                request.durWarningEnabled(),
                request.medicationDelayEnabled(),
                request.medicationDelayThresholdMinutes(),
                request.familySafetyEnabled(),
                request.familySafetyThresholdHours(),
                request.medicationCompleteEnabled()
        );
        return NotificationSettingsResponse.from(settings);
    }

    @Transactional
    public NotificationSettingsResponse applyPreset(
            final Long caregiverId,
            final Long seniorId,
            final CareMode mode
    ) {
        validateCaregiverAccess(caregiverId, seniorId);
        final NotificationSettings settings = findOrCreate(caregiverId, seniorId);
        if (mode == CareMode.MANAGED) {
            settings.applyIntensivePreset();
        } else {
            settings.applyStandardPreset();
        }
        return NotificationSettingsResponse.from(settings);
    }

    private NotificationSettings findOrCreate(final Long caregiverId, final Long seniorId) {
        return notificationSettingsRepository.findByCaregiverIdAndSeniorId(caregiverId, seniorId)
                .orElseGet(() -> notificationSettingsRepository.save(
                        NotificationSettings.createWithStandardPreset(caregiverId, seniorId)));
    }

    private void validateCaregiverAccess(final Long caregiverId, final Long seniorId) {
        careRelationRepository.findByCaregiverIdAndSeniorIdAndDeletedAtIsNull(caregiverId, seniorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CARE_RELATION_NOT_FOUND));
    }
}
