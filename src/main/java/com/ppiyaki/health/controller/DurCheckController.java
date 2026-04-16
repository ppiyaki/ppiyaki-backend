package com.ppiyaki.health.controller;

import com.ppiyaki.health.controller.dto.DurCheckListResponse;
import com.ppiyaki.health.controller.dto.DurCheckResponse;
import com.ppiyaki.health.service.DurCheckService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/medicines/{medicineId}")
@ConditionalOnProperty(prefix = "mfds.api", name = "service-key")
public class DurCheckController {

    private final DurCheckService durCheckService;

    public DurCheckController(final DurCheckService durCheckService) {
        this.durCheckService = durCheckService;
    }

    @PostMapping("/dur-check")
    public ResponseEntity<DurCheckResponse> check(
            @AuthenticationPrincipal final Long userId,
            @PathVariable final Long medicineId,
            @RequestParam(defaultValue = "false") final boolean forceRefresh
    ) {
        final DurCheckResponse response = durCheckService.check(userId, medicineId, forceRefresh);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/dur-check/latest")
    public ResponseEntity<DurCheckResponse> getLatest(
            @AuthenticationPrincipal final Long userId,
            @PathVariable final Long medicineId
    ) {
        final DurCheckResponse response = durCheckService.getLatest(userId, medicineId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/dur-checks")
    public ResponseEntity<DurCheckListResponse> getHistory(
            @AuthenticationPrincipal final Long userId,
            @PathVariable final Long medicineId,
            @RequestParam(defaultValue = "10") final int limit
    ) {
        final DurCheckListResponse response = durCheckService.getHistory(userId, medicineId, limit);
        return ResponseEntity.ok(response);
    }
}
