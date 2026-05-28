package com.ppiyaki.notification.controller.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record WellbeingPingCreateRequest(
        @NotNull @Positive Long caregiverId
) {
}
