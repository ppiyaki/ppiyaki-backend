package com.ppiyaki.user.controller.dto;

import com.ppiyaki.user.domain.Gender;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ProfileUpdateRequest(
        @NotBlank String nickname,

        @Min(1) @Max(6) Integer profileImage,

        String profileImageObjectKey,

        Gender gender
) {

    @AssertTrue(message = "profileImage and profileImageObjectKey are mutually exclusive")
    public boolean isProfileImageExclusive() {
        return profileImage == null || profileImageObjectKey == null;
    }
}
