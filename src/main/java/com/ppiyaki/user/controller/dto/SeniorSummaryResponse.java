package com.ppiyaki.user.controller.dto;

import com.ppiyaki.user.domain.CareMode;
import com.ppiyaki.user.domain.Gender;
import com.ppiyaki.user.domain.User;
import java.time.LocalDate;

public record SeniorSummaryResponse(
        Long id,
        String nickname,
        LocalDate birthDate,
        Gender gender,
        CareMode careMode,
        Integer profileImage,
        String profileImageUrl
) {

    public static SeniorSummaryResponse from(final User user, final String profileImageUrl) {
        return new SeniorSummaryResponse(
                user.getId(),
                user.getNickname(),
                user.getBirthDate(),
                user.getGender(),
                user.getCareMode(),
                user.getProfileImage(),
                profileImageUrl
        );
    }
}
