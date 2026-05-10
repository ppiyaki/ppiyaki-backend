package com.ppiyaki.notification.controller.dto;

import com.ppiyaki.user.CareMode;
import jakarta.validation.constraints.NotNull;

public record PresetApplyRequest(
        @NotNull CareMode careMode
) {
}
