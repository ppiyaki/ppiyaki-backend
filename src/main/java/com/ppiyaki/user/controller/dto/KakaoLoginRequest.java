package com.ppiyaki.user.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record KakaoLoginRequest(
        @NotBlank String code,
        @NotBlank String redirectUri
) {
}
