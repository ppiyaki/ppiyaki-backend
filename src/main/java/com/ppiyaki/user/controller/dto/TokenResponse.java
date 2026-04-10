package com.ppiyaki.user.controller.dto;

public record TokenResponse(
        String accessToken,
        String refreshToken
) {
}
