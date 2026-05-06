package com.ppiyaki.user.controller.dto;

import java.util.List;

public record SeniorDeviceListResponse(
        List<SeniorDeviceResponse> responses
) {
}
