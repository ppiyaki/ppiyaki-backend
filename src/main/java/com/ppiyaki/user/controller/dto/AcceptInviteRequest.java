package com.ppiyaki.user.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AcceptInviteRequest(
        @NotBlank @Pattern(regexp = "^[A-Z0-9]{6}$", message = "Invite code must be 6 uppercase alphanumeric characters") String inviteCode
) {
}
