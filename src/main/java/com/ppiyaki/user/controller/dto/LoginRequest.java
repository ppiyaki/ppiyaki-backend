package com.ppiyaki.user.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank String loginId,
        @NotBlank String password
) {
}
