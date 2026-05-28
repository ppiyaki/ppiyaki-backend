package com.ppiyaki.notification.controller;

import com.ppiyaki.notification.controller.dto.WellbeingPingCreateRequest;
import com.ppiyaki.notification.service.WellbeingPingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications/wellbeing-pings")
public class WellbeingPingController {

    private final WellbeingPingService wellbeingPingService;

    public WellbeingPingController(final WellbeingPingService wellbeingPingService) {
        this.wellbeingPingService = wellbeingPingService;
    }

    @PostMapping
    public ResponseEntity<Void> send(
            @AuthenticationPrincipal final Long userId,
            @Valid @RequestBody final WellbeingPingCreateRequest request
    ) {
        wellbeingPingService.send(userId, request.caregiverId());
        return ResponseEntity.noContent().build();
    }
}
