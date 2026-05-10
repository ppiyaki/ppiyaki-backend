package com.ppiyaki.notification.controller;

import com.ppiyaki.notification.controller.dto.DeviceTokenRegisterRequest;
import com.ppiyaki.notification.controller.dto.DeviceTokenResponse;
import com.ppiyaki.notification.service.DeviceTokenService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/devices")
public class DeviceTokenController {

    private final DeviceTokenService deviceTokenService;

    public DeviceTokenController(final DeviceTokenService deviceTokenService) {
        this.deviceTokenService = deviceTokenService;
    }

    @PostMapping
    public ResponseEntity<DeviceTokenResponse> register(
            @AuthenticationPrincipal final Long userId,
            @Valid @RequestBody final DeviceTokenRegisterRequest request
    ) {
        final DeviceTokenResponse response = deviceTokenService.register(userId, request.token(), request.platform());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{tokenId}")
    public ResponseEntity<Void> deactivate(
            @AuthenticationPrincipal final Long userId,
            @PathVariable final Long tokenId
    ) {
        deviceTokenService.deactivate(userId, tokenId);
        return ResponseEntity.noContent().build();
    }
}
