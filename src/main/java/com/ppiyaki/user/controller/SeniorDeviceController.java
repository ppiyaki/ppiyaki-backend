package com.ppiyaki.user.controller;

import com.ppiyaki.user.controller.dto.SeniorDeviceListResponse;
import com.ppiyaki.user.service.SeniorDeviceService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/seniors/{seniorId}/devices")
public class SeniorDeviceController {

    private final SeniorDeviceService seniorDeviceService;

    public SeniorDeviceController(final SeniorDeviceService seniorDeviceService) {
        this.seniorDeviceService = seniorDeviceService;
    }

    @GetMapping
    public ResponseEntity<SeniorDeviceListResponse> readDevices(
            @AuthenticationPrincipal final Long userId,
            @PathVariable final Long seniorId
    ) {
        final SeniorDeviceListResponse seniorDeviceListResponse = seniorDeviceService.readDevices(userId, seniorId);
        return ResponseEntity.ok(seniorDeviceListResponse);
    }

    @DeleteMapping("/{deviceId}")
    public ResponseEntity<Void> revokeDevice(
            @AuthenticationPrincipal final Long userId,
            @PathVariable final Long seniorId,
            @PathVariable final Long deviceId
    ) {
        seniorDeviceService.revokeDevice(userId, seniorId, deviceId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> revokeAllDevices(
            @AuthenticationPrincipal final Long userId,
            @PathVariable final Long seniorId
    ) {
        seniorDeviceService.revokeAllDevices(userId, seniorId);
        return ResponseEntity.noContent().build();
    }
}
