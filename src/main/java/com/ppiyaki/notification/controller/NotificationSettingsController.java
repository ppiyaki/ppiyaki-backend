package com.ppiyaki.notification.controller;

import com.ppiyaki.notification.controller.dto.NotificationSettingsResponse;
import com.ppiyaki.notification.controller.dto.NotificationSettingsUpdateRequest;
import com.ppiyaki.notification.controller.dto.PresetApplyRequest;
import com.ppiyaki.notification.service.NotificationSettingsService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/seniors/{seniorId}/notification-settings")
public class NotificationSettingsController {

    private final NotificationSettingsService notificationSettingsService;

    public NotificationSettingsController(final NotificationSettingsService notificationSettingsService) {
        this.notificationSettingsService = notificationSettingsService;
    }

    @GetMapping
    public ResponseEntity<NotificationSettingsResponse> read(
            @AuthenticationPrincipal final Long caregiverId,
            @PathVariable final Long seniorId
    ) {
        return ResponseEntity.ok(notificationSettingsService.read(caregiverId, seniorId));
    }

    @PutMapping
    public ResponseEntity<NotificationSettingsResponse> update(
            @AuthenticationPrincipal final Long caregiverId,
            @PathVariable final Long seniorId,
            @Valid @RequestBody final NotificationSettingsUpdateRequest request
    ) {
        return ResponseEntity.ok(notificationSettingsService.update(caregiverId, seniorId, request));
    }

    @PostMapping("/preset")
    public ResponseEntity<NotificationSettingsResponse> applyPreset(
            @AuthenticationPrincipal final Long caregiverId,
            @PathVariable final Long seniorId,
            @Valid @RequestBody final PresetApplyRequest request
    ) {
        return ResponseEntity.ok(notificationSettingsService.applyPreset(caregiverId, seniorId, request.mode()));
    }
}
