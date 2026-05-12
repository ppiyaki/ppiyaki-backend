package com.ppiyaki.user.controller;

import com.ppiyaki.user.controller.dto.CodeLoginRequest;
import com.ppiyaki.user.controller.dto.InviteCodeRequest;
import com.ppiyaki.user.controller.dto.InviteCodeResponse;
import com.ppiyaki.user.controller.dto.LoginResponse;
import com.ppiyaki.user.controller.dto.SeniorSummaryResponse;
import com.ppiyaki.user.service.CareRelationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class CareRelationController {

    private final CareRelationService careRelationService;

    public CareRelationController(final CareRelationService careRelationService) {
        this.careRelationService = careRelationService;
    }

    @GetMapping("/care-relations/seniors")
    @PreAuthorize("hasRole('CAREGIVER')")
    public ResponseEntity<List<SeniorSummaryResponse>> readSeniors(
            @AuthenticationPrincipal final Long userId
    ) {
        final List<SeniorSummaryResponse> responses = careRelationService.readSeniors(userId);
        return ResponseEntity.ok(responses);
    }

    @PostMapping("/care-relations/invite")
    @PreAuthorize("hasRole('CAREGIVER')")
    public ResponseEntity<InviteCodeResponse> createInviteCode(
            @AuthenticationPrincipal final Long userId,
            @Valid @RequestBody final InviteCodeRequest inviteCodeRequest
    ) {
        final InviteCodeResponse inviteCodeResponse = careRelationService.createInviteCode(
                userId, inviteCodeRequest.seniorId());
        return ResponseEntity.status(HttpStatus.CREATED).body(inviteCodeResponse);
    }

    @PostMapping("/auth/code-login")
    public ResponseEntity<LoginResponse> codeLogin(
            @Valid @RequestBody final CodeLoginRequest codeLoginRequest,
            final HttpServletRequest request
    ) {
        final String clientIp = resolveClientIp(request);
        final LoginResponse loginResponse = careRelationService.codeLogin(
                codeLoginRequest.code(), clientIp);
        return ResponseEntity.ok(loginResponse);
    }

    @DeleteMapping("/seniors/{seniorId}/logout")
    @PreAuthorize("hasRole('CAREGIVER')")
    public ResponseEntity<Void> forceLogoutSenior(
            @AuthenticationPrincipal final Long userId,
            @PathVariable final Long seniorId
    ) {
        careRelationService.forceLogoutSenior(userId, seniorId);
        return ResponseEntity.noContent().build();
    }

    private String resolveClientIp(final HttpServletRequest request) {
        final String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        final String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }
        return request.getRemoteAddr();
    }
}
