package com.ppiyaki.user.controller.dto;

import com.ppiyaki.user.domain.Gender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SeniorProfileUpdateRequest(
        @NotBlank String nickname,

        @NotNull Gender gender
) {
}
