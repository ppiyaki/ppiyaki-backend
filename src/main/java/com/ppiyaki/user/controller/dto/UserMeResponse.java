package com.ppiyaki.user.controller.dto;

import com.ppiyaki.user.domain.User;

public record UserMeResponse(
        Long id,
        String nickname,
        String role,
        String gender,
        Integer profileImage,
        String profileImageUrl,
        boolean isOnboarded,
        String careMode,
        MealTimesResponse mealTimes
) {

    public static UserMeResponse from(final User user, final String profileImageUrl) {
        final String roleName = user.getRole() != null ? user.getRole().name() : null;
        final String genderName = user.getGender() != null ? user.getGender().name() : null;
        final boolean onboarded = user.getRole() != null;
        final String careModeName = user.getCareMode() != null ? user.getCareMode().name() : null;
        final MealTimesResponse mealTimes = MealTimesResponse.from(user);
        return new UserMeResponse(user.getId(), user.getNickname(), roleName, genderName,
                user.getProfileImage(), profileImageUrl, onboarded, careModeName, mealTimes);
    }
}
