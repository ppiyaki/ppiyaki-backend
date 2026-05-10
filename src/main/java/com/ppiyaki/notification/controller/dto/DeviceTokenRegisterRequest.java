package com.ppiyaki.notification.controller.dto;

import com.ppiyaki.notification.DevicePlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DeviceTokenRegisterRequest(
        @NotBlank String token,
        @NotNull DevicePlatform platform
) {
}
