package com.ppiyaki.user.controller.dto;

import jakarta.validation.constraints.NotNull;

public record InviteCodeRequest(
        @NotNull Long seniorId
) {
}
