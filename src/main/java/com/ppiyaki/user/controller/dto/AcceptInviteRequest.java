package com.ppiyaki.user.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record AcceptInviteRequest(
        @NotBlank String inviteCode
) {
}
