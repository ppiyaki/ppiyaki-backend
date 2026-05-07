package com.ppiyaki.user.controller.dto;

import com.ppiyaki.user.Gender;
import com.ppiyaki.user.NotificationMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record OnboardingRequest(
        @NotBlank String nickname,
        @NotEmpty @Valid List<SeniorEntry> seniors
) {

    public record SeniorEntry(
            @NotBlank String nickname,
            @NotNull Gender gender,
            @NotNull NotificationMode notificationMode
    ) {
    }
}
