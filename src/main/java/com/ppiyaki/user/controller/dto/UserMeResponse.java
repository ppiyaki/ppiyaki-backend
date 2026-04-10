package com.ppiyaki.user.controller.dto;

import com.ppiyaki.user.User;

public record UserMeResponse(
        Long id,
        String nickname,
        String role,
        boolean isOnboarded
) {

    public static UserMeResponse from(final User user) {
        final String roleName = user.getRole() != null ? user.getRole().name() : null;
        final boolean onboarded = user.getRole() != null;
        return new UserMeResponse(user.getId(), user.getNickname(), roleName, onboarded);
    }
}
