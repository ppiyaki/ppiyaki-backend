package com.ppiyaki.notification.controller.dto;

import com.ppiyaki.notification.NotificationSettings;

public record NotificationSettingsResponse(
        Long caregiverId,
        Long seniorId,
        boolean durWarningEnabled,
        boolean medicationDelayEnabled,
        int medicationDelayThresholdMinutes,
        boolean familySafetyEnabled,
        int familySafetyThresholdHours,
        boolean medicationCompleteEnabled,
        boolean prescriptionReviewRequestEnabled
) {

    public static NotificationSettingsResponse from(final NotificationSettings settings) {
        return new NotificationSettingsResponse(
                settings.getCaregiverId(),
                settings.getSeniorId(),
                settings.isDurWarningEnabled(),
                settings.isMedicationDelayEnabled(),
                settings.getMedicationDelayThresholdMinutes(),
                settings.isFamilySafetyEnabled(),
                settings.getFamilySafetyThresholdHours(),
                settings.isMedicationCompleteEnabled(),
                settings.isPrescriptionReviewRequestEnabled()
        );
    }
}
