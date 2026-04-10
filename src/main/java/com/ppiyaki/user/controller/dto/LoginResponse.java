package com.ppiyaki.user.controller.dto;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        boolean isOnboarded
) {
}
