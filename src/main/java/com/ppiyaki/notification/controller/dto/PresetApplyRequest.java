package com.ppiyaki.notification.controller.dto;

import com.ppiyaki.notification.NotificationMode;
import jakarta.validation.constraints.NotNull;

public record PresetApplyRequest(
        @NotNull NotificationMode mode
) {
}
