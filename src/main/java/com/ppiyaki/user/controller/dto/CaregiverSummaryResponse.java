package com.ppiyaki.user.controller.dto;

import com.ppiyaki.user.domain.User;

public record CaregiverSummaryResponse(
        Long id,
        String nickname,
        Integer profileImage,
        String profileImageUrl
) {

    public static CaregiverSummaryResponse from(final User user, final String profileImageUrl) {
        return new CaregiverSummaryResponse(
                user.getId(),
                user.getNickname(),
                user.getProfileImage(),
                profileImageUrl
        );
    }
}
