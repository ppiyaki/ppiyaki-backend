package com.ppiyaki.user.controller;

import com.ppiyaki.user.controller.dto.SeniorCreateRequest;
import com.ppiyaki.user.controller.dto.SeniorCreateResponse;
import com.ppiyaki.user.service.SeniorService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/seniors")
public class SeniorController {

    private final SeniorService seniorService;

    public SeniorController(final SeniorService seniorService) {
        this.seniorService = seniorService;
    }

    @PostMapping
    public ResponseEntity<SeniorCreateResponse> createSenior(
            @AuthenticationPrincipal final Long userId,
            @Valid @RequestBody final SeniorCreateRequest seniorCreateRequest
    ) {
        final SeniorCreateResponse seniorCreateResponse = seniorService.createSenior(userId, seniorCreateRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(seniorCreateResponse);
    }
}
