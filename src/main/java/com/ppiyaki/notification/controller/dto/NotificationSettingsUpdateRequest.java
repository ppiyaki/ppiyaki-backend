package com.ppiyaki.notification.controller.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record NotificationSettingsUpdateRequest(
        @NotNull Boolean durWarningEnabled,
        @NotNull Boolean medicationDelayEnabled,
        @NotNull @Min(1) Integer medicationDelayThresholdMinutes,
        @NotNull Boolean familySafetyEnabled,
        @NotNull @Min(1) Integer familySafetyThresholdHours,
        @NotNull Boolean medicationCompleteEnabled,
        @NotNull Boolean prescriptionReviewRequestEnabled
) {
}
