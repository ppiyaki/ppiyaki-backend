package com.ppiyaki.user.controller.dto;

import java.time.LocalDateTime;

public record InviteCodeResponse(
        String inviteCode,
        LocalDateTime expiresAt
) {
}
