package com.ppiyaki.user.controller.dto;

import com.ppiyaki.user.domain.User;

public record UserMeResponse(
        Long id,
        String nickname,
        String role,
        boolean isOnboarded,
        MealTimesResponse mealTimes
) {

    public static UserMeResponse from(final User user) {
        final String roleName = user.getRole() != null ? user.getRole().name() : null;
        final boolean onboarded = user.getRole() != null;
        final MealTimesResponse mealTimes = MealTimesResponse.from(user);
        return new UserMeResponse(user.getId(), user.getNickname(), roleName, onboarded, mealTimes);
    }
}
