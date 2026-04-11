package com.ppiyaki.user.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record SignupRequest(
        @NotBlank String loginId,
        @NotBlank String password,
        @NotBlank String nickname
) {
}
