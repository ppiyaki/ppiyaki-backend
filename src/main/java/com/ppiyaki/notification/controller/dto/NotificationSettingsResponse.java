package com.ppiyaki.notification.controller.dto;

import com.ppiyaki.notification.NotificationMode;
import com.ppiyaki.notification.NotificationSettings;

public record NotificationSettingsResponse(
        Long caregiverId,
        Long seniorId,
        NotificationMode mode,
        boolean durWarningEnabled,
        boolean medicationDelayEnabled,
        int medicationDelayThresholdMinutes,
        boolean familySafetyEnabled,
        int familySafetyThresholdHours,
        boolean medicationCompleteEnabled
) {

    public static NotificationSettingsResponse from(final NotificationSettings settings) {
        return new NotificationSettingsResponse(
                settings.getCaregiverId(),
                settings.getSeniorId(),
                settings.getMode(),
                settings.isDurWarningEnabled(),
                settings.isMedicationDelayEnabled(),
                settings.getMedicationDelayThresholdMinutes(),
                settings.isFamilySafetyEnabled(),
                settings.getFamilySafetyThresholdHours(),
                settings.isMedicationCompleteEnabled()
        );
    }
}
