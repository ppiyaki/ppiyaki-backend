package com.ppiyaki.user.controller;

import com.ppiyaki.user.controller.dto.OnboardingRequest;
import com.ppiyaki.user.controller.dto.OnboardingResponse;
import com.ppiyaki.user.service.OnboardingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class OnboardingController {

    private final OnboardingService onboardingService;

    public OnboardingController(final OnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    @PostMapping("/onboarding")
    @PreAuthorize("hasRole('CAREGIVER')")
    public ResponseEntity<OnboardingResponse> onboard(
            @AuthenticationPrincipal final Long userId,
            @Valid @RequestBody final OnboardingRequest onboardingRequest
    ) {
        final OnboardingResponse onboardingResponse = onboardingService.onboard(userId, onboardingRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(onboardingResponse);
    }
}
