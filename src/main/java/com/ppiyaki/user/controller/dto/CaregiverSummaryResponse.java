package com.ppiyaki.user.controller.dto;

import com.ppiyaki.user.domain.User;

public record CaregiverSummaryResponse(
        Long id,
        String nickname
) {

    public static CaregiverSummaryResponse from(final User user) {
        return new CaregiverSummaryResponse(
                user.getId(),
                user.getNickname()
        );
    }
}
