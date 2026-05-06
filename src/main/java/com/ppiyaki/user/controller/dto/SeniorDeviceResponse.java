package com.ppiyaki.user.controller.dto;

import com.ppiyaki.user.SeniorDevice;
import java.time.LocalDateTime;

public record SeniorDeviceResponse(
        Long id,
        String deviceId,
        String deviceName,
        String status,
        LocalDateTime lastUsedAt
) {

    public static SeniorDeviceResponse from(final SeniorDevice seniorDevice) {
        return new SeniorDeviceResponse(
                seniorDevice.getId(),
                seniorDevice.getDeviceId(),
                seniorDevice.getDeviceName(),
                seniorDevice.getStatus().name(),
                seniorDevice.getLastUsedAt()
        );
    }
}
