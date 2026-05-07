package com.ppiyaki.user.controller.dto;

import java.util.List;

public record OnboardingResponse(
        String caregiverNickname,
        List<SeniorResult> seniors
) {

    public record SeniorResult(
            Long seniorId,
            String nickname,
            Long petId
    ) {
    }
}
