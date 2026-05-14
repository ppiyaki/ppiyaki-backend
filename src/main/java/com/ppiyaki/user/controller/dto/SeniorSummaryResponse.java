package com.ppiyaki.user.controller.dto;

import com.ppiyaki.user.domain.CareMode;
import com.ppiyaki.user.domain.Gender;
import com.ppiyaki.user.domain.User;
import java.time.LocalDate;

public record SeniorSummaryResponse(
        Long id,
        String nickname,
        LocalDate dob,
        Gender gender,
        CareMode careMode
) {

    public static SeniorSummaryResponse from(final User user) {
        return new SeniorSummaryResponse(
                user.getId(),
                user.getNickname(),
                user.getDob(),
                user.getGender(),
                user.getCareMode()
        );
    }
}
