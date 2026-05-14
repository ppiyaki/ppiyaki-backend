package com.ppiyaki.notification.controller.dto;

import com.ppiyaki.user.domain.CareMode;
import jakarta.validation.constraints.NotNull;

public record PresetApplyRequest(
        @NotNull CareMode careMode
) {
}
