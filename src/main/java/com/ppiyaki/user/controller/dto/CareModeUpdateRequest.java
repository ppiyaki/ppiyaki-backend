package com.ppiyaki.user.controller.dto;

import com.ppiyaki.user.domain.CareMode;
import jakarta.validation.constraints.NotNull;

public record CareModeUpdateRequest(
        @NotNull CareMode careMode
) {
}
