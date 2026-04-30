package com.ppiyaki.user.controller.dto;

import com.ppiyaki.user.CareMode;

public record CareModeResponse(
        Long userId,
        CareMode careMode
) {
}
