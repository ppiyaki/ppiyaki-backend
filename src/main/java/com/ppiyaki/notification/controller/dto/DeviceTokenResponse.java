package com.ppiyaki.notification.controller.dto;

import com.ppiyaki.notification.DevicePlatform;
import com.ppiyaki.notification.DeviceToken;

public record DeviceTokenResponse(
        Long tokenId,
        DevicePlatform platform,
        boolean isActive
) {

    public static DeviceTokenResponse from(final DeviceToken deviceToken) {
        return new DeviceTokenResponse(
                deviceToken.getId(),
                deviceToken.getPlatform(),
                deviceToken.getIsActive()
        );
    }
}
